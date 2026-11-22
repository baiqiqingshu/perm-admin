<template>
  <div class="query-page">
    <!-- 筛选区 -->
    <section class="panel-card hoverable filter-card">
      <header class="card-head">
        <span class="head-icon"><el-icon :size="15"><Filter /></el-icon></span>
        <h3>筛选条件</h3>
      </header>
      <el-form inline class="filter-form" @submit.prevent>
        <TableCascade v-model="cascade" />
        <el-form-item label="权限显示名">
          <el-select
            v-model="filters.permissionLabel"
            placeholder="全部"
            clearable
            filterable
            style="width: 140px"
          >
            <el-option v-for="l in permissionLabelOptions" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select
            v-model="filters.scopeLabel"
            placeholder="全部"
            clearable
            filterable
            style="width: 140px"
          >
            <el-option v-for="l in scopeLabelOptions" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="filters.keyword"
            placeholder="工号 / 姓名"
            clearable
            style="width: 160px"
            @keyup.enter="doQuery"
          >
            <template #prefix>
              <el-icon class="input-prefix-icon"><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" :loading="loading" @click="doQuery">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button @click="onReset">
            <el-icon><RefreshLeft /></el-icon>&nbsp;重置
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 结果区 -->
    <section class="panel-card hoverable result-card">
      <header class="card-head">
        <span class="head-icon"><el-icon :size="15"><List /></el-icon></span>
        <h3>查询结果</h3>
        <span class="muted result-count">共 {{ rows.length }} 条</span>
        <span class="spacer"></span>
        <el-button
          type="danger"
          plain
          :disabled="!selection.length"
          :loading="previewing"
          @click="onBatchDelete"
        >
          <el-icon><Delete /></el-icon>&nbsp;批量删除
          <template v-if="selection.length">（{{ selection.length }}）</template>
        </el-button>
      </header>

      <el-alert
        v-if="errors.length"
        type="warning"
        :closable="false"
        title="部分权限表查询失败"
        class="errors-alert"
      >
        <ul class="errors-list">
          <li v-for="(e, i) in errors" :key="i">{{ formatError(e) }}</li>
        </ul>
      </el-alert>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="rows"
        stripe
        empty-text="暂无数据，调整筛选条件后重新查询"
        @selection-change="(v) => (selection = v)"
      >
        <el-table-column type="selection" width="42" />
        <el-table-column prop="personId" label="工号" width="110" show-overflow-tooltip />
        <el-table-column prop="personName" label="用户名称" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.personName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" min-width="110" show-overflow-tooltip />
        <el-table-column prop="environmentName" label="环境" width="100" show-overflow-tooltip />
        <el-table-column prop="databaseName" label="数据库" min-width="110" show-overflow-tooltip />
        <el-table-column prop="tableName" label="权限表" min-width="150" show-overflow-tooltip />
        <el-table-column label="数据范围" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.scopeLabel }}<span class="muted">（{{ row.scopeValue }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="权限" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" effect="plain" class="perm-tag">{{ row.permissionLabel }}</el-tag>
            <span class="muted">{{ row.permissionValue }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="onRowDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <SqlPreviewDialog
      v-model="dialogVisible"
      :groups="previewGroups"
      :skipped="previewSkipped"
      :unsupported="previewUnsupported"
      :errors="previewErrors"
      :executing="executing"
      @confirm="onConfirmExecute"
    />

    <ExecuteResultDialog
      v-model="resultVisible"
      :groups="previewGroups"
      :skipped="previewSkipped"
      :unsupported="previewUnsupported"
      :errors="previewErrors"
      :results="execResults"
      @confirm="onResultDone"
    />
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import TableCascade from '../components/TableCascade.vue'
import SqlPreviewDialog from '../components/SqlPreviewDialog.vue'
import ExecuteResultDialog from '../components/ExecuteResultDialog.vue'
import { queryPermissions, previewSql, executeSql, extractErrorMessage } from '../api'
import { loadConfig, collectPermissionLabels, collectScopeLabels } from '../store/config'

const cascade = reactive({ project: '', environment: '', database: '', tableCode: '' })
const filters = reactive({ permissionLabel: '', scopeLabel: '', keyword: '' })

const rows = ref([])
const errors = ref([])
const loading = ref(false)
const selection = ref([])
const tableRef = ref()

const dialogVisible = ref(false)
const previewGroups = ref([])
const previewSkipped = ref([])
const previewUnsupported = ref([])
const previewErrors = ref([])
const executing = ref(false)
const previewing = ref(false)

const resultVisible = ref(false)
const execResults = ref([])

const permissionLabelOptions = computed(() => collectPermissionLabels())
const scopeLabelOptions = computed(() => collectScopeLabels())

onMounted(() => loadConfig())

async function doQuery() {
  loading.value = true
  try {
    const params = {}
    if (cascade.project) params.project = cascade.project
    if (cascade.environment) params.environment = cascade.environment
    if (cascade.database) params.database = cascade.database
    if (cascade.tableCode) params.tableCode = cascade.tableCode
    if (filters.permissionLabel) params.permissionLabel = filters.permissionLabel
    if (filters.scopeLabel) params.scopeLabel = filters.scopeLabel
    if (filters.keyword) params.keyword = filters.keyword
    const data = await queryPermissions(params)
    rows.value = data?.rows || []
    errors.value = data?.errors || []
  } finally {
    loading.value = false
  }
}

function onReset() {
  cascade.project = ''
  cascade.environment = ''
  cascade.database = ''
  cascade.tableCode = ''
  filters.permissionLabel = ''
  filters.scopeLabel = ''
  filters.keyword = ''
  errors.value = []
  doQuery()
}

function pickItem(row) {
  return {
    project: row.project,
    environment: row.environment,
    database: row.database,
    tableCode: row.tableCode,
    personId: row.personId,
    personName: row.personName,
    scopeValue: row.scopeValue,
    permissionValue: row.permissionValue
  }
}

async function openDeletePreview(items) {
  if (!items.length) return
  previewing.value = true
  try {
    const data = await previewSql({ type: 'delete', items })
    previewGroups.value = data?.groups || []
    previewSkipped.value = data?.skipped || []
    previewUnsupported.value = data?.unsupported || []
    previewErrors.value = data?.errors || []
    if (!previewGroups.value.length) {
      ElMessage.warning('未生成任何 SQL')
      return
    }
    dialogVisible.value = true
  } finally {
    previewing.value = false
  }
}

function onRowDelete(row) {
  openDeletePreview([pickItem(row)])
}

function onBatchDelete() {
  openDeletePreview(selection.value.map(pickItem))
}

async function onConfirmExecute(groups) {
  executing.value = true
  let results
  try {
    const data = await executeSql({ groups })
    results = data?.results || []
  } catch (e) {
    // execute 请求本身失败（网络错/500）：全部组按失败进结果弹窗
    const msg = extractErrorMessage(e)
    results = (groups || []).map((g) => ({
      key: g.key,
      success: false,
      rows: 0,
      error: msg
    }))
  } finally {
    executing.value = false
  }
  execResults.value = results
  dialogVisible.value = false
  resultVisible.value = true
}

/** 结果弹窗点「确定」后：刷新列表并清空已选 */
async function onResultDone() {
  selection.value = []
  tableRef.value?.clearSelection()
  await doQuery()
}

function formatError(e) {
  if (typeof e === 'string') return e
  if (!e || typeof e !== 'object') return String(e)
  const where =
    e.tableName || e.tableCode || e.dbName || e.dbCode || e.database || e.key || ''
  const msg = e.error || e.message || JSON.stringify(e)
  return where ? `${where}：${msg}` : msg
}
</script>

<style scoped>
.query-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.card-head .spacer {
  flex: 1;
}
.head-icon {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-600);
  background: var(--brand-50);
  border: 1px solid var(--brand-100);
}
.card-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-900);
}
.result-count {
  margin-left: 4px;
  font-variant-numeric: tabular-nums;
}

.filter-card {
  padding-bottom: 6px;
}
.filter-form {
  margin-bottom: -14px;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
}
.filter-form :deep(.el-form-item) {
  margin-bottom: 14px;
  margin-right: 16px;
}
.filter-form :deep(.el-form-item__label) {
  font-weight: 500;
}
.filter-actions {
  margin-left: auto;
}
.input-prefix-icon {
  color: var(--ink-500);
}

.errors-alert {
  margin-bottom: 12px;
}
.errors-list {
  margin: 4px 0 0;
  padding-left: 18px;
  font-weight: 400;
}
.perm-tag {
  margin-right: 6px;
}

@media (max-width: 720px) {
  .filter-form :deep(.el-select),
  .filter-form :deep(.el-input) {
    width: 100% !important;
  }
  .filter-form :deep(.el-form-item) {
    width: 100%;
    margin-right: 0;
  }
}
</style>
