<template>
  <div class="add-page">
    <!-- 人员 -->
    <section class="panel-card hoverable section">
      <header class="section-head">
        <span class="section-icon"><el-icon :size="16"><User /></el-icon></span>
        <div class="section-title">
          <h3>批量人员</h3>
          <p>每行一条：工号 或 工号,姓名（兼容中文逗号，按工号去重）</p>
        </div>
        <el-tag v-if="persons.length" size="small" effect="plain" class="count-tag">
          已解析 {{ persons.length }} 人<template v-if="dupCount"> · 去重 {{ dupCount }} 行</template>
        </el-tag>
      </header>
      <el-input
        v-model="personsText"
        type="textarea"
        :rows="5"
        :placeholder="'例如：\nu001,张三\nu002\nu003，李四'"
      />
    </section>

    <!-- 目标范围 -->
    <section class="panel-card hoverable section">
      <header class="section-head">
        <span class="section-icon"><el-icon :size="16"><Aim /></el-icon></span>
        <div class="section-title">
          <h3>目标范围</h3>
          <p>四级留空即表示全部；下级选项随上级已选自动收敛</p>
        </div>
        <el-button
          v-if="hasTargetSelection"
          text
          size="small"
          @click="resetTargets"
        >
          <el-icon><RefreshLeft /></el-icon>&nbsp;重置目标
        </el-button>
      </header>

      <div class="select-grid">
        <div class="select-field">
          <label class="field-label">项目</label>
          <el-select
            v-model="sel.projects"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="2"
            placeholder="留空 = 全部项目"
          >
            <el-option
              v-for="o in projectOptions"
              :key="o.code"
              :value="o.code"
              :label="`${o.name}（${o.code}）`"
              :disabled="o.disabled"
            >
              <div class="opt-row">
                <span class="opt-label">{{ o.name }}<span class="opt-code">{{ o.code }}</span></span>
                <span class="opt-meta" :class="{ warn: o.disabled }">
                  {{ o.disabled ? '不含所选权限/范围' : `${o.count} 表` }}
                </span>
              </div>
            </el-option>
          </el-select>
        </div>

        <div class="select-field">
          <label class="field-label">环境</label>
          <el-select
            v-model="sel.envs"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="2"
            placeholder="留空 = 全部环境"
          >
            <el-option
              v-for="o in envOptions"
              :key="o.code"
              :value="o.code"
              :label="`${o.name}（${o.code}）`"
              :disabled="o.disabled"
            >
              <div class="opt-row">
                <span class="opt-label">{{ o.name }}<span class="opt-code">{{ o.code }}</span></span>
                <span class="opt-meta" :class="{ warn: o.disabled }">
                  {{ o.disabled ? '不含所选权限/范围' : `${o.count} 表` }}
                </span>
              </div>
            </el-option>
          </el-select>
        </div>

        <div class="select-field">
          <label class="field-label">数据库</label>
          <el-select
            v-model="sel.dbs"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="2"
            placeholder="留空 = 全部数据库"
          >
            <el-option
              v-for="o in dbOptions"
              :key="o.code"
              :value="o.code"
              :label="`${o.name}（${o.code}）`"
              :disabled="o.disabled"
            >
              <div class="opt-row">
                <span class="opt-label">{{ o.name }}<span class="opt-code">{{ o.code }}</span></span>
                <span class="opt-meta" :class="{ warn: o.disabled }">
                  {{ o.disabled ? '不含所选权限/范围' : `${o.count} 表` }}
                </span>
              </div>
            </el-option>
          </el-select>
        </div>

        <div class="select-field">
          <label class="field-label">权限表</label>
          <el-select
            v-model="sel.tables"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="2"
            placeholder="留空 = 全部权限表"
          >
            <el-option
              v-for="o in tableOptions"
              :key="o.code"
              :value="o.code"
              :label="`${o.name}（${o.tableName}）`"
              :disabled="o.disabled"
            >
              <div class="opt-row">
                <span class="opt-label">{{ o.name }}<span class="opt-code">{{ o.tableName }}</span></span>
                <span class="opt-meta" :class="{ warn: o.disabled }">
                  {{ o.disabled ? '不含所选权限/范围' : o.count > 1 ? `${o.count} 处` : '' }}
                </span>
              </div>
            </el-option>
          </el-select>
        </div>
      </div>
    </section>

    <!-- 授权项 -->
    <section class="panel-card hoverable section">
      <header class="section-head">
        <span class="section-icon"><el-icon :size="16"><Key /></el-icon></span>
        <div class="section-title">
          <h3>权限与数据范围</h3>
          <p>按显示名勾选；某表须同时支持全部已选项才会被写入，不满足的目标会自动取消勾选</p>
        </div>
      </header>

      <div class="select-grid two">
        <div class="select-field">
          <label class="field-label">权限（显示名）</label>
          <el-select
            v-model="permLabels"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="3"
            placeholder="选择权限显示名"
          >
            <el-option
              v-for="o in permLabelOptions"
              :key="o.label"
              :value="o.label"
              :label="o.label"
            >
              <div class="opt-row">
                <span class="opt-label">{{ o.label }}</span>
                <span class="opt-meta">{{ o.count }} 表</span>
              </div>
            </el-option>
          </el-select>
        </div>

        <div class="select-field">
          <label class="field-label">数据范围（显示名）</label>
          <el-select
            v-model="scopeLabels"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="3"
            placeholder="选择数据范围显示名"
          >
            <el-option
              v-for="o in scopeLabelOptions"
              :key="o.label"
              :value="o.label"
              :label="o.label"
            >
              <div class="opt-row">
                <span class="opt-label">{{ o.label }}</span>
                <span class="opt-meta">{{ o.count }} 表</span>
              </div>
            </el-option>
          </el-select>
        </div>
      </div>
    </section>

    <!-- 摘要 + 操作 -->
    <section class="panel-card summary-bar">
      <div class="summary-text">
        <template v-if="effective.length">
          <span class="summary-line">
            将写入 <b>{{ summary.projects }}</b> 个项目 · <b>{{ summary.envs }}</b> 个环境 ·
            <b>{{ summary.dbs }}</b> 个库 · <b>{{ summary.tables }}</b> 张表
            <template v-if="estRows">，预计约 <b>{{ estRows }}</b> 条插入</template>
          </span>
          <span class="summary-detail muted">
            <template v-if="estRows">
              ≈ {{ persons.length }} 人 × {{ permLabels.length }} 权限 ×
              {{ scopeLabels.length }} 范围 × {{ summary.tables }} 表（去重结果以预览为准）
            </template>
            <template v-else>
              {{ persons.length ? '勾选权限与数据范围后显示预计条数' : '填写人员后显示预计条数' }}
            </template>
          </span>
          <span class="summary-tables muted">{{ tablePreview }}</span>
        </template>
        <template v-else>
          <span class="summary-line muted">当前条件未命中任何权限表</span>
          <span class="summary-detail muted">调整目标范围或减少权限/范围勾选</span>
        </template>
      </div>
      <div class="summary-actions">
        <el-button @click="resetForm">清空</el-button>
        <el-button type="primary" :loading="previewing" @click="onPreview">
          <el-icon><View /></el-icon>&nbsp;预览 SQL
        </el-button>
      </div>
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
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import SqlPreviewDialog from '../components/SqlPreviewDialog.vue'
import ExecuteResultDialog from '../components/ExecuteResultDialog.vue'
import { previewSql, executeSql, extractErrorMessage } from '../api'
import { loadConfig, collectTableEntries } from '../store/config'

const personsText = ref('')

/** 四级目标多选（存 code；空数组 = 该级全部） */
const sel = reactive({ projects: [], envs: [], dbs: [], tables: [] })
const permLabels = ref([])
const scopeLabels = ref([])

const dialogVisible = ref(false)
const previewGroups = ref([])
const previewSkipped = ref([])
const previewUnsupported = ref([])
const previewErrors = ref([])
const previewing = ref(false)
const executing = ref(false)

const resultVisible = ref(false)
const execResults = ref([])

onMounted(() => loadConfig())

/* ---------- 人员解析（按工号去重，兼容中英文逗号） ---------- */
const parsed = computed(() => {
  const list = []
  const seen = new Set()
  let dup = 0
  for (const raw of String(personsText.value || '').split(/\r?\n/)) {
    const line = raw.trim()
    if (!line) continue
    const idx = line.search(/[,，]/)
    const id = idx < 0 ? line : line.slice(0, idx).trim()
    const name = idx < 0 ? '' : line.slice(idx + 1).trim()
    if (!id) continue
    if (seen.has(id)) {
      dup++
      continue
    }
    seen.add(id)
    list.push({ id, name })
  }
  return { list, dup }
})
const persons = computed(() => parsed.value.list)
const dupCount = computed(() => parsed.value.dup)

/* ---------- 目标条目与级联可见性 ---------- */
const entries = computed(() => collectTableEntries())

const inProjects = (e) => !sel.projects.length || sel.projects.includes(e.project)
const inEnvs = (e) => inProjects(e) && (!sel.envs.length || sel.envs.includes(e.env))
const inDbs = (e) => inEnvs(e) && (!sel.dbs.length || sel.dbs.includes(e.db))
const inTables = (e) => inDbs(e) && (!sel.tables.length || sel.tables.includes(e.tableCode))

/** 一张表必须同时支持全部已选 permissionLabels 与 scopeLabels 才算合格 */
const labelsActive = computed(() => permLabels.value.length + scopeLabels.value.length > 0)
const qualifies = (e) =>
  permLabels.value.every((l) => e.permLabels.has(l)) &&
  scopeLabels.value.every((l) => e.scopeLabels.has(l))

/** 最终将被写入的 (db, table) 目标 */
const effective = computed(() => entries.value.filter((e) => inTables(e) && qualifies(e)))

/* ---------- 选项构建（按 code 去重 + 合格性禁用） ---------- */
function buildOptions(list, keyOf, nameOf, extraOf) {
  const map = new Map()
  for (const e of list) {
    const code = keyOf(e)
    let o = map.get(code)
    if (!o) {
      o = { code, name: nameOf(e), count: 0, disabled: false }
      map.set(code, o)
    }
    o.count++
    if (extraOf) extraOf(o, e)
  }
  return [...map.values()]
}

const projectOptions = computed(() =>
  buildOptions(
    entries.value,
    (e) => e.project,
    (e) => e.projectName
  ).map((o) => ({
    ...o,
    disabled: labelsActive.value && !entries.value.some((e) => e.project === o.code && qualifies(e))
  }))
)

const envVisible = computed(() => entries.value.filter(inProjects))
const envOptions = computed(() =>
  buildOptions(
    envVisible.value,
    (e) => e.env,
    (e) => e.envName
  ).map((o) => ({
    ...o,
    disabled: labelsActive.value && !envVisible.value.some((e) => e.env === o.code && qualifies(e))
  }))
)

const dbVisible = computed(() => entries.value.filter(inEnvs))
const dbOptions = computed(() =>
  buildOptions(
    dbVisible.value,
    (e) => e.db,
    (e) => e.dbName
  ).map((o) => ({
    ...o,
    disabled: labelsActive.value && !dbVisible.value.some((e) => e.db === o.code && qualifies(e))
  }))
)

const tableVisible = computed(() => entries.value.filter(inDbs))
const tableOptions = computed(() =>
  buildOptions(
    tableVisible.value,
    (e) => e.tableCode,
    (e) => e.tableTitle,
    (o, e) => {
      o.tableName = e.tableName
    }
  ).map((o) => ({
    ...o,
    disabled:
      labelsActive.value && !tableVisible.value.some((e) => e.tableCode === o.code && qualifies(e))
  }))
)

/* ---------- 权限/范围 label 选项（当前目标范围内的覆盖表数） ---------- */
const selScope = computed(() => entries.value.filter(inTables))
function labelOptions(field) {
  const map = new Map()
  for (const e of selScope.value) {
    for (const l of e[field]) {
      map.set(l, (map.get(l) || 0) + 1)
    }
  }
  return [...map.entries()].map(([label, count]) => ({ label, count }))
}
const permLabelOptions = computed(() => labelOptions('permLabels'))
const scopeLabelOptions = computed(() => labelOptions('scopeLabels'))

/* ---------- 自动取消勾选 ---------- */
function setIfDiff(arr, next) {
  if (next.length !== arr.length || next.some((v, i) => v !== arr[i])) {
    return next
  }
  return arr
}

/** label 勾选变化：自顶向下修剪掉无法满足全部已选 label 的目标 */
watch([permLabels, scopeLabels], () => {
  if (!labelsActive.value) return // 清空 label 时不主动恢复选择
  let removed = 0
  const prune = (arr, keep) => {
    const next = arr.filter(keep)
    removed += arr.length - next.length
    return next
  }
  sel.projects = prune(sel.projects, (c) =>
    entries.value.some((e) => e.project === c && qualifies(e))
  )
  sel.envs = prune(sel.envs, (c) =>
    entries.value.some((e) => e.env === c && inProjects(e) && qualifies(e))
  )
  sel.dbs = prune(sel.dbs, (c) =>
    entries.value.some((e) => e.db === c && inEnvs(e) && qualifies(e))
  )
  sel.tables = prune(sel.tables, (c) =>
    entries.value.some((e) => e.tableCode === c && inDbs(e) && qualifies(e))
  )
  if (removed > 0) {
    ElMessage.info(`已自动移除 ${removed} 项不支持所选权限/范围的目标`)
  }
})

/** 上级选择变化：把下级已选修剪到仍在可选项内的部分 */
watch(
  () => [sel.projects, sel.envs, sel.dbs],
  () => {
    sel.envs = setIfDiff(
      sel.envs,
      sel.envs.filter((c) => envOptions.value.some((o) => o.code === c))
    )
    sel.dbs = setIfDiff(
      sel.dbs,
      sel.dbs.filter((c) => dbOptions.value.some((o) => o.code === c))
    )
    sel.tables = setIfDiff(
      sel.tables,
      sel.tables.filter((c) => tableOptions.value.some((o) => o.code === c))
    )
  },
  { deep: true }
)

/* ---------- 摘要 ---------- */
const summary = computed(() => ({
  projects: new Set(effective.value.map((e) => e.project)).size,
  envs: new Set(effective.value.map((e) => `${e.project}|${e.env}`)).size,
  dbs: new Set(effective.value.map((e) => e.db)).size,
  tables: effective.value.length
}))

/** 每张目标表实际将生成的行数 = 命中权限数 × 命中范围数；M = 人数 × 合计 */
const estRows = computed(() => {
  if (!persons.value.length || !permLabels.value.length || !scopeLabels.value.length) return 0
  const perTable = effective.value.reduce(
    (sum, e) =>
      sum +
      permLabels.value.filter((l) => e.permLabels.has(l)).length *
        scopeLabels.value.filter((l) => e.scopeLabels.has(l)).length,
    0
  )
  return persons.value.length * perTable
})

const tablePreview = computed(() => {
  const names = effective.value.map((e) => `${e.dbName} / ${e.tableTitle || e.tableName}`)
  const uniq = [...new Set(names)]
  if (uniq.length <= 6) return uniq.join('、')
  return uniq.slice(0, 6).join('、') + ` 等 ${uniq.length} 张`
})

const hasTargetSelection = computed(
  () => sel.projects.length + sel.envs.length + sel.dbs.length + sel.tables.length > 0
)

function resetTargets() {
  sel.projects = []
  sel.envs = []
  sel.dbs = []
  sel.tables = []
}

/* ---------- 提交 ---------- */
async function onPreview() {
  if (!persons.value.length) {
    ElMessage.warning('请填写批量人员（每行：工号 或 工号,姓名）')
    return
  }
  if (!permLabels.value.length) {
    ElMessage.warning('请选择权限显示名')
    return
  }
  if (!scopeLabels.value.length) {
    ElMessage.warning('请选择数据范围显示名')
    return
  }
  if (!effective.value.length) {
    ElMessage.warning('当前条件未命中任何权限表')
    return
  }
  const targets = {}
  if (sel.projects.length) targets.projects = [...sel.projects]
  if (sel.envs.length) targets.environments = [...sel.envs]
  if (sel.dbs.length) targets.databases = [...sel.dbs]
  if (sel.tables.length) targets.tableCodes = [...sel.tables]

  previewing.value = true
  try {
    const data = await previewSql({
      type: 'insert',
      targets,
      persons: persons.value,
      permissionLabels: [...permLabels.value],
      scopeLabels: [...scopeLabels.value]
    })
    previewGroups.value = data?.groups || []
    previewSkipped.value = data?.skipped || []
    previewUnsupported.value = data?.unsupported || []
    previewErrors.value = data?.errors || []
    if (!previewGroups.value.length && !previewSkipped.value.length) {
      ElMessage.warning('未生成任何 SQL')
      return
    }
    dialogVisible.value = true
  } finally {
    previewing.value = false
  }
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

/** 结果弹窗点「确定」后：全部成功才重置表单（保持原逻辑，失败可修正重试） */
function onResultDone() {
  const allOk =
    execResults.value.length > 0 && execResults.value.every((r) => r.success)
  if (allOk) resetForm()
}

function resetForm() {
  personsText.value = ''
  resetTargets()
  permLabels.value = []
  scopeLabels.value = []
}
</script>

<style scoped>
.add-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 900px;
}

.section {
  scroll-margin-top: 76px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}
.section-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-600);
  background: var(--brand-50);
  border: 1px solid var(--brand-100);
}
.section-title h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-900);
}
.section-title p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--ink-500);
}
.count-tag {
  margin-left: auto;
  border-radius: 999px;
}

.select-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 16px;
}
.select-grid.two {
  grid-template-columns: 1fr 1fr;
}
.select-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.field-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-500);
  letter-spacing: 0.02em;
}
.select-field :deep(.el-select) {
  width: 100%;
}

/* 下拉选项行：左 label 右 meta */
.opt-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.opt-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.opt-code {
  margin-left: 6px;
  color: var(--ink-500);
  font-size: 12px;
}
.opt-meta {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--ink-500);
}
.opt-meta.warn {
  color: var(--el-color-warning);
}

/* 摘要条 */
.summary-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 24px;
}
.summary-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.summary-line {
  font-size: 13px;
  color: var(--ink-700);
}
.summary-line b {
  color: var(--brand-600);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.summary-tables {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.summary-actions {
  margin-left: auto;
  flex-shrink: 0;
  display: flex;
  gap: 10px;
}
.summary-actions .el-button + .el-button {
  margin-left: 0;
}

@media (max-width: 720px) {
  .select-grid,
  .select-grid.two {
    grid-template-columns: 1fr;
  }
  .summary-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .summary-actions {
    margin-left: 0;
    justify-content: flex-end;
  }
}
</style>
