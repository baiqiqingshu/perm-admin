<template>
  <div class="page-card">
    <el-form label-width="110px" class="add-form">
      <el-form-item label="批量人员" required>
        <el-input
          v-model="personsText"
          type="textarea"
          :rows="5"
          :placeholder="'每行一条：工号 或 工号,姓名（姓名可空）\n例如：\nu001,张三\nu002'"
        />
        <div class="muted tip">已解析 {{ persons.length }} 人</div>
      </el-form-item>

      <el-form-item label="目标权限表" required>
        <div class="cascade-inline">
          <el-form inline>
            <TableCascade v-model="cascade" @change="onCascadeChange" />
          </el-form>
        </div>
      </el-form-item>

      <el-form-item label="勾选权限" required>
        <el-checkbox-group v-model="checkedPermissions" :disabled="!tableNode">
          <el-checkbox
            v-for="p in tableNode?.permissions || []"
            :key="p.value"
            :value="p.value"
          >
            {{ p.label }}<span class="muted">（{{ p.value }}）</span>
          </el-checkbox>
        </el-checkbox-group>
        <div v-if="!tableNode" class="muted tip">请先选择权限表</div>
        <div v-else-if="!(tableNode.permissions || []).length" class="muted tip">
          该表未配置可选权限
        </div>
      </el-form-item>

      <el-form-item label="勾选数据范围" required>
        <el-checkbox-group v-model="checkedScopes" :disabled="!tableNode">
          <el-checkbox
            v-for="s in tableNode?.scopes || []"
            :key="s.value"
            :value="s.value"
          >
            {{ s.label }}<span class="muted">（{{ s.value }}）</span>
          </el-checkbox>
        </el-checkbox-group>
        <div v-if="!tableNode" class="muted tip">请先选择权限表</div>
        <div v-else-if="!(tableNode.scopes || []).length" class="muted tip">
          该表未配置可选数据范围
        </div>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="previewing" @click="onPreview">
          预览SQL
        </el-button>
        <el-button @click="resetForm">清空</el-button>
        <span v-if="planCount" class="muted plan-tip">
          预计新增 {{ planCount }} 条（人员 × 权限 × 范围）
        </span>
      </el-form-item>
    </el-form>

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
import { previewSql, executeSql } from '../api'
import { loadConfig } from '../store/config'

const personsText = ref('')
const cascade = reactive({ project: '', environment: '', database: '', tableCode: '' })
const tableNode = ref(null)
const checkedPermissions = ref([])
const checkedScopes = ref([])

const dialogVisible = ref(false)
const previewGroups = ref([])
const previewing = ref(false)
const executing = ref(false)

onMounted(() => loadConfig())

const persons = computed(() => parsePersons(personsText.value))
const planCount = computed(
  () => persons.value.length * checkedPermissions.value.length * checkedScopes.value.length
)

function parsePersons(text) {
  const list = []
  const seen = new Set()
  for (const raw of String(text || '').split(/\r?\n/)) {
    const line = raw.trim()
    if (!line) continue
    const idx = line.search(/[,，]/)
    const id = idx < 0 ? line : line.slice(0, idx).trim()
    const name = idx < 0 ? '' : line.slice(idx + 1).trim()
    if (!id || seen.has(id)) continue
    seen.add(id)
    list.push({ id, name })
  }
  return list
}

function onCascadeChange({ tableNode: tn }) {
  tableNode.value = tn || null
  checkedPermissions.value = []
  checkedScopes.value = []
}

async function onPreview() {
  if (!persons.value.length) {
    ElMessage.warning('请填写批量人员（每行：工号 或 工号,姓名）')
    return
  }
  if (!cascade.project || !cascade.environment || !cascade.database || !cascade.tableCode) {
    ElMessage.warning('请完整选择 项目 → 环境 → 数据库 → 权限表')
    return
  }
  if (!checkedPermissions.value.length) {
    ElMessage.warning('请勾选权限')
    return
  }
  if (!checkedScopes.value.length) {
    ElMessage.warning('请勾选数据范围')
    return
  }
  previewing.value = true
  try {
    const data = await previewSql({
      type: 'insert',
      project: cascade.project,
      environment: cascade.environment,
      database: cascade.database,
      tableCode: cascade.tableCode,
      persons: persons.value,
      permissions: [...checkedPermissions.value],
      scopes: [...checkedScopes.value]
    })
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

async function onConfirmExecute(groups) {
  executing.value = true
  try {
    const data = await executeSql({ groups })
    const results = data?.results || []
    const allOk = results.length > 0 && results.every((r) => r.success)
    if (!results.length) ElMessage.info('执行完成')
    for (const r of results) {
      if (r.success) {
        ElMessage.success(`${r.key}：执行成功，影响 ${r.rows ?? 0} 行`)
      } else {
        ElMessage.error(`${r.key}：执行失败 - ${r.error || '未知错误'}`)
      }
    }
    dialogVisible.value = false
    if (allOk) {
      resetForm()
      ElMessage.success('新增权限完成')
    }
  } finally {
    executing.value = false
  }
}

function resetForm() {
  personsText.value = ''
  cascade.project = ''
  cascade.environment = ''
  cascade.database = ''
  cascade.tableCode = ''
  tableNode.value = null
  checkedPermissions.value = []
  checkedScopes.value = []
}
</script>

<style scoped>
.add-form {
  max-width: 860px;
}
.cascade-inline :deep(.el-form-item) {
  margin-bottom: 0;
}
.tip {
  margin-top: 4px;
  width: 100%;
}
.plan-tip {
  margin-left: 12px;
}
</style>
