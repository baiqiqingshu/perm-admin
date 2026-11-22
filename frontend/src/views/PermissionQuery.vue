<template>
  <div class="page-card">
    <el-form inline @submit.prevent>
      <TableCascade v-model="cascade" />
      <el-form-item label="权限显示名">
        <el-select
          v-model="filters.permissionLabel"
          placeholder="权限显示名"
          clearable
          filterable
          style="width: 140px"
        >
          <el-option v-for="l in permissionLabelOptions" :key="l" :label="l" :value="l" />
        </el-select>
      </el-form-item>
      <el-form-item label="数据范围显示名">
        <el-select
          v-model="filters.scopeLabel"
          placeholder="数据范围显示名"
          clearable
          filterable
          style="width: 150px"
        >
          <el-option v-for="l in scopeLabelOptions" :key="l" :label="l" :value="l" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字">
        <el-input
          v-model="filters.keyword"
          placeholder="工号 / 姓名"
          clearable
          style="width: 150px"
          @keyup.enter="doQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="doQuery">
          <el-icon><Search /></el-icon>&nbsp;查询
        </el-button>
        <el-button @click="onReset">
          <el-icon><RefreshLeft /></el-icon>&nbsp;重置
        </el-button>
      </el-form-item>
    </el-form>

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

    <div class="toolbar">
      <el-button
        type="danger"
        :disabled="!selection.length"
        :loading="previewing"
        @click="onBatchDelete"
      >
        <el-icon><Delete /></el-icon>&nbsp;批量删除
        <template v-if="selection.length">（{{ selection.length }}）</template>
      </el-button>
      <span class="spacer"></span>
      <span class="muted">共 {{ rows.length }} 条</span>
    </div>

    <el-table
      v-loading="loading"
      :data="rows"
      border
      stripe
      empty-text="暂无数据"
      @selection-change="(v) => (selection = v)"
    >
      <el-table-column type="selection" width="45" />
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
          {{ row.permissionLabel }}<span class="muted">（{{ row.permissionValue }}）</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" link size="small" @click="onRowDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <SqlPreviewDialog
      v-model="dialogVisible"
      :groups="previewGroups"
      :executing="executing"
      @confirm="onConfirmExecute"
    />
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import TableCascade from '../components/TableCascade.vue'
import SqlPreviewDialog from '../components/SqlPreviewDialog.vue'
import { queryPermissions, previewSql, executeSql } from '../api'
import { loadConfig, collectPermissionLabels, collectScopeLabels } from '../store/config'

const cascade = reactive({ project: '', environment: '', database: '', tableCode: '' })
const filters = reactive({ permissionLabel: '', scopeLabel: '', keyword: '' })

const rows = ref([])
const errors = ref([])
const loading = ref(false)
const selection = ref([])

const dialogVisible = ref(false)
const previewGroups = ref([])
const executing = ref(false)
const previewing = ref(false)

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
    const groups = data?.groups || []
    if (!groups.length) {
      ElMessage.warning('未生成任何 SQL')
      return
    }
    previewGroups.value = groups
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
  try {
    const data = await executeSql({ groups })
    const results = data?.results || []
    if (!results.length) ElMessage.info('执行完成')
    for (const r of results) {
      if (r.success) {
        ElMessage.success(`${r.key}：执行成功，影响 ${r.rows ?? 0} 行`)
      } else {
        ElMessage.error(`${r.key}：执行失败 - ${r.error || '未知错误'}`)
      }
    }
    dialogVisible.value = false
    await doQuery()
  } finally {
    executing.value = false
  }
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
.errors-alert {
  margin-bottom: 12px;
}
.errors-list {
  margin: 4px 0 0;
  padding-left: 18px;
}
</style>
