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
            v-model="filters.permissionLabels"
            placeholder="全部"
            multiple
            clearable
            filterable
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="1"
            style="width: 190px"
          >
            <el-option v-for="l in permissionLabelOptions" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select
            v-model="filters.scopeLabels"
            placeholder="全部"
            multiple
            clearable
            filterable
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="1"
            style="width: 190px"
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
        <span class="muted result-count">{{ resultCountText }}</span>
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

      <!-- 多列排序规则编辑器 -->
      <div v-if="sortRules.length" class="sort-panel" aria-label="当前排序">
        <span class="sort-panel-title">排序</span>
        <div v-for="(rule, i) in sortRules" :key="rule.prop" class="sort-chip">
          <span class="rule-order">{{ i + 1 }}</span>
          <span class="sort-field">{{ columnLabel(rule.prop) }}</span>
          <button
            type="button"
            class="chip-action direction-action"
            :title="rule.dir === 'asc' ? '升序，点击切换为降序' : '降序，点击切换为升序'"
            :aria-label="`切换${columnLabel(rule.prop)}排序方向`"
            @click="toggleRuleDirection(rule)"
          >
            <el-icon><SortUp v-if="rule.dir === 'asc'" /><SortDown v-else /></el-icon>
            <span>{{ rule.dir === 'asc' ? '升序' : '降序' }}</span>
          </button>
          <button
            type="button"
            class="chip-action"
            :disabled="i === 0"
            title="前移"
            :aria-label="`前移${columnLabel(rule.prop)}排序`"
            @click="moveSortRule(i, -1)"
          >
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <button
            type="button"
            class="chip-action"
            :disabled="i === sortRules.length - 1"
            title="后移"
            :aria-label="`后移${columnLabel(rule.prop)}排序`"
            @click="moveSortRule(i, 1)"
          >
            <el-icon><ArrowRight /></el-icon>
          </button>
          <button
            type="button"
            class="chip-action danger"
            title="删除排序"
            :aria-label="`删除${columnLabel(rule.prop)}排序`"
            @click="removeSortRule(i)"
          >
            <el-icon><Close /></el-icon>
          </button>
        </div>
        <el-button link size="small" class="clear-sort" @click="clearSortRules">清空排序</el-button>
      </div>

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
        :data="visibleRows"
        border
        stripe
        :empty-text="rows.length ? '当前表头筛选条件下暂无数据' : '暂无数据，调整筛选条件后重新查询'"
        @selection-change="(v) => (selection = v)"
      >
        <el-table-column type="selection" width="42" :resizable="false" />
        <el-table-column width="46" align="center" :resizable="false">
          <template #header>#</template>
          <template #default="{ $index }">
            <span class="muted row-index">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-for="col in dataColumns"
          :key="col.key"
          :width="col.width"
          :resizable="true"
          show-overflow-tooltip
        >
          <template #header>
            <div class="column-head">
              <span class="column-title" :title="col.label">{{ col.label }}</span>
              <span class="column-actions">
                <button
                  type="button"
                  class="col-action"
                  :class="{ active: sortIndexFor(col.key) !== -1 }"
                  :title="sortButtonTitle(col)"
                  :aria-label="sortButtonTitle(col)"
                  @click="toggleSort(col.key)"
                >
                  <el-icon>
                    <SortUp v-if="sortDirectionFor(col.key) === 'asc'" />
                    <SortDown v-else-if="sortDirectionFor(col.key) === 'desc'" />
                    <Sort v-else />
                  </el-icon>
                  <span v-if="sortIndexFor(col.key) !== -1" class="action-badge">
                    {{ sortIndexFor(col.key) + 1 }}
                  </span>
                </button>
                <el-popover
                  placement="bottom-start"
                  :width="260"
                  trigger="click"
                  @show="onFilterPopoverShow(col.key)"
                >
                  <template #reference>
                    <button
                      type="button"
                      class="col-action"
                      :class="{ active: isFilterActive(col.key) }"
                      :title="`筛选${col.label}`"
                      :aria-label="`筛选${col.label}`"
                    >
                      <el-icon><Filter /></el-icon>
                      <span v-if="activeFilterCount(col.key)" class="action-badge">
                        {{ activeFilterCount(col.key) }}
                      </span>
                    </button>
                  </template>
                  <div class="filter-popover">
                    <el-input
                      v-model="filterSearch[col.key]"
                      size="small"
                      clearable
                      :placeholder="`搜索${col.label}`"
                    >
                      <template #prefix>
                        <el-icon><Search /></el-icon>
                      </template>
                    </el-input>
                    <div class="filter-toolbar">
                      <el-button
                        link
                        type="primary"
                        size="small"
                        :disabled="!visibleFilterOptions(col.key).length || allVisibleOptionsSelected(col.key)"
                        @click="selectVisibleFilterOptions(col.key)"
                      >
                        全选当前搜索
                      </el-button>
                      <el-button
                        link
                        size="small"
                        :disabled="!activeFilterCount(col.key)"
                        @click="clearColumnFilter(col.key)"
                      >
                        清空
                      </el-button>
                    </div>
                    <div class="filter-option-list">
                      <el-checkbox-group
                        v-if="visibleFilterOptions(col.key).length"
                        v-model="headerFilters[col.key]"
                        class="filter-checkbox-group"
                      >
                        <el-checkbox
                          v-for="(opt, optIndex) in visibleFilterOptions(col.key)"
                          :key="`${col.key}-${optIndex}-${opt.value}`"
                          :value="opt.value"
                          class="filter-option"
                        >
                          <span class="filter-option-text" :title="opt.label">{{ opt.label }}</span>
                        </el-checkbox>
                      </el-checkbox-group>
                      <div v-else class="filter-empty">{{ filterEmptyText(col.key) }}</div>
                    </div>
                    <div class="filter-meta">
                      已选 {{ activeFilterCount(col.key) }} / 共 {{ filterOptionTotal(col.key) }} 项
                    </div>
                  </div>
                </el-popover>
              </span>
            </div>
          </template>
          <template #default="{ row }">
            <template v-if="col.type === 'scope'">
              {{ row.scopeLabel }}<span class="muted">（{{ row.scopeValue }}）</span>
            </template>
            <span v-else-if="col.type === 'permission'" class="permission-cell">
              <el-tag size="small" effect="plain" class="perm-tag">
                {{ row.permissionLabel || row.permissionValue || '—' }}
              </el-tag>
              <span v-if="row.permissionValue" class="muted">（{{ row.permissionValue }}）</span>
            </span>
            <span v-else>{{ displayCell(row, col) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right" :resizable="false">
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

function createCascadeState() {
  return { projects: [], environments: [], databases: [], tableCodes: [] }
}

const cascade = ref(createCascadeState())
const filters = reactive({ permissionLabels: [], scopeLabels: [], keyword: '' })

const rows = ref([])
const sortRules = ref([])

function displayScopeText(row) {
  return `${row.scopeLabel ?? ''}（${row.scopeValue ?? ''}）`
}

function displayPermissionText(row) {
  return `${row.permissionLabel ?? ''}（${row.permissionValue ?? ''}）`
}

const dataColumns = [
  { key: 'personId', label: '工号', width: 120, type: 'text', getValue: (row) => row.personId ?? '' },
  { key: 'personName', label: '用户名称', width: 130, type: 'text', getValue: (row) => row.personName ?? '' },
  { key: 'projectName', label: '项目', width: 130, type: 'text', getValue: (row) => row.projectName ?? '' },
  { key: 'environmentName', label: '环境', width: 110, type: 'text', getValue: (row) => row.environmentName ?? '' },
  { key: 'databaseName', label: '数据库', width: 140, type: 'text', getValue: (row) => row.databaseName ?? '' },
  { key: 'tableName', label: '权限表', width: 180, type: 'text', getValue: (row) => row.tableName ?? '' },
  { key: 'scope', label: '数据范围', width: 180, type: 'scope', getValue: displayScopeText },
  { key: 'permission', label: '权限', width: 150, type: 'permission', getValue: displayPermissionText }
]
const columnMap = Object.fromEntries(dataColumns.map((col) => [col.key, col]))
const collator = new Intl.Collator('zh-Hans-CN', { numeric: true, sensitivity: 'base' })

const headerFilters = reactive(Object.fromEntries(dataColumns.map((col) => [col.key, []])))
const filterSearch = reactive(Object.fromEntries(dataColumns.map((col) => [col.key, ''])))

const columnFilterOptions = computed(() => {
  const result = {}
  for (const col of dataColumns) {
    const seen = new Set()
    const options = []
    for (const row of rows.value) {
      const value = normalizeFilterValue(col.getValue(row))
      if (seen.has(value)) continue
      seen.add(value)
      options.push({ value, label: value === '' ? '（空）' : value })
    }
    options.sort((a, b) => collator.compare(a.label, b.label))
    result[col.key] = options
  }
  return result
})

const filteredRows = computed(() => {
  const active = dataColumns
    .map((col) => ({ col, values: new Set(headerFilters[col.key] || []) }))
    .filter((entry) => entry.values.size)
  if (!active.length) return rows.value
  return rows.value.filter((row) =>
    active.every(({ col, values }) => values.has(normalizeFilterValue(col.getValue(row))))
  )
})

const visibleRows = computed(() => {
  const source = filteredRows.value
  const rules = sortRules.value.filter((rule) => columnMap[rule.prop])
  if (!rules.length) return source
  return source
    .map((row, index) => ({ row, index }))
    .sort((a, b) => {
      for (const rule of rules) {
        const col = columnMap[rule.prop]
        const compared = compareCellValues(col.getValue(a.row), col.getValue(b.row))
        if (compared !== 0) return rule.dir === 'desc' ? -compared : compared
      }
      return a.index - b.index
    })
    .map((entry) => entry.row)
})

const resultCountText = computed(() => {
  const total = rows.value.length
  const filtered = filteredRows.value.length
  return filtered === total ? `共 ${total} 条` : `筛选后 ${filtered} / 共 ${total} 条`
})

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

function normalizeFilterValue(value) {
  return value === null || value === undefined ? '' : String(value)
}

function compareCellValues(a, b) {
  if (typeof a === 'number' && typeof b === 'number') return a - b
  return collator.compare(String(a ?? ''), String(b ?? ''))
}

function displayCell(row, col) {
  const value = col.getValue(row)
  return value === null || value === undefined || value === '' ? '—' : value
}

function columnLabel(prop) {
  return columnMap[prop]?.label || prop
}

function sortIndexFor(prop) {
  return sortRules.value.findIndex((rule) => rule.prop === prop)
}

function sortDirectionFor(prop) {
  const index = sortIndexFor(prop)
  return index === -1 ? '' : sortRules.value[index].dir
}

function sortButtonTitle(col) {
  const dir = sortDirectionFor(col.key)
  if (!dir) return `按${col.label}升序排序`
  return dir === 'asc' ? `${col.label}当前升序，点击切换为降序` : `${col.label}当前降序，点击切换为升序`
}

function toggleSort(prop) {
  const index = sortIndexFor(prop)
  if (index === -1) {
    sortRules.value.push({ prop, dir: 'asc' })
    return
  }
  sortRules.value[index].dir = sortRules.value[index].dir === 'asc' ? 'desc' : 'asc'
}

function toggleRuleDirection(rule) {
  rule.dir = rule.dir === 'asc' ? 'desc' : 'asc'
}

function moveSortRule(index, offset) {
  const target = index + offset
  if (target < 0 || target >= sortRules.value.length) return
  const next = [...sortRules.value]
  const [rule] = next.splice(index, 1)
  next.splice(target, 0, rule)
  sortRules.value = next
}

function removeSortRule(index) {
  sortRules.value.splice(index, 1)
}

function clearSortRules() {
  sortRules.value = []
}

function visibleFilterOptions(prop) {
  const options = columnFilterOptions.value[prop] || []
  const keyword = (filterSearch[prop] || '').trim().toLocaleLowerCase()
  if (!keyword) return options
  return options.filter((opt) => opt.label.toLocaleLowerCase().includes(keyword))
}

function activeFilterCount(prop) {
  return (headerFilters[prop] || []).length
}

function isFilterActive(prop) {
  return activeFilterCount(prop) > 0
}

function filterOptionTotal(prop) {
  return (columnFilterOptions.value[prop] || []).length
}

function filterEmptyText(prop) {
  return filterOptionTotal(prop) ? '无匹配选项' : '当前结果暂无选项'
}

function allVisibleOptionsSelected(prop) {
  const visible = visibleFilterOptions(prop)
  if (!visible.length) return false
  const selected = new Set(headerFilters[prop] || [])
  return visible.every((opt) => selected.has(opt.value))
}

function selectVisibleFilterOptions(prop) {
  const selected = new Set(headerFilters[prop] || [])
  for (const opt of visibleFilterOptions(prop)) selected.add(opt.value)
  headerFilters[prop] = [...selected]
}

function clearColumnFilter(prop) {
  headerFilters[prop] = []
}

function clearAllHeaderFilters() {
  for (const col of dataColumns) {
    headerFilters[col.key] = []
    filterSearch[col.key] = ''
  }
}

function onFilterPopoverShow(prop) {
  filterSearch[prop] = ''
}

function pruneHeaderFilters() {
  for (const col of dataColumns) {
    const valid = new Set((columnFilterOptions.value[col.key] || []).map((opt) => opt.value))
    const current = headerFilters[col.key] || []
    const next = current.filter((value) => valid.has(value))
    if (next.length !== current.length) headerFilters[col.key] = next
  }
}

function pruneSortRules() {
  sortRules.value = sortRules.value.filter((rule) => columnMap[rule.prop])
}

function appendArrayParam(params, key, value) {
  const values = Array.isArray(value) ? value.filter((v) => v !== null && v !== undefined && v !== '') : []
  if (values.length) params[key] = [...new Set(values)]
}

async function doQuery() {
  loading.value = true
  try {
    const params = {}
    appendArrayParam(params, 'projects', cascade.value?.projects)
    appendArrayParam(params, 'environments', cascade.value?.environments)
    appendArrayParam(params, 'databases', cascade.value?.databases)
    appendArrayParam(params, 'tableCodes', cascade.value?.tableCodes)
    appendArrayParam(params, 'permissionLabels', filters.permissionLabels)
    appendArrayParam(params, 'scopeLabels', filters.scopeLabels)
    if (filters.keyword) params.keyword = filters.keyword
    const data = await queryPermissions(params)
    rows.value = data?.rows || []
    errors.value = data?.errors || []
    pruneHeaderFilters()
    pruneSortRules()
  } finally {
    loading.value = false
  }
}

function onReset() {
  cascade.value = createCascadeState()
  filters.permissionLabels = []
  filters.scopeLabels = []
  filters.keyword = ''
  clearSortRules()
  clearAllHeaderFilters()
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

.sort-panel {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 10px;
  background: var(--brand-50);
  border: 1px solid var(--brand-100);
  border-radius: var(--radius-md);
}
.sort-panel-title {
  color: var(--brand-700);
  font-size: 12px;
  font-weight: 600;
  margin-right: 2px;
}
.sort-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-height: 30px;
  padding: 2px 4px 2px 6px;
  background: #fff;
  border: 1px solid var(--brand-100);
  border-radius: 999px;
}
.rule-order {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--brand-600);
  color: #fff;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.sort-field {
  max-width: 92px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink-900);
  font-size: 12px;
  font-weight: 600;
  padding: 0 2px;
}
.chip-action {
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: var(--radius-sm);
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--ink-500);
  background: transparent;
  cursor: pointer;
  transition: color var(--dur) var(--ease), background-color var(--dur) var(--ease);
}
.chip-action:hover:not(:disabled) {
  color: var(--brand-600);
  background: var(--brand-50);
}
.chip-action:disabled {
  color: var(--ink-400);
  cursor: not-allowed;
  opacity: 0.55;
}
.chip-action.danger:hover:not(:disabled) {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
.direction-action {
  width: auto;
  gap: 2px;
  padding: 0 6px;
  color: var(--brand-700);
  font-size: 12px;
  font-weight: 600;
}
.clear-sort {
  margin-left: auto;
}
.row-index {
  font-size: 12px;
}

.column-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  min-width: 0;
}
.column-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.column-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.col-action {
  position: relative;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: var(--radius-sm);
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--ink-500);
  background: transparent;
  cursor: pointer;
  transition: color var(--dur) var(--ease), background-color var(--dur) var(--ease);
}
.col-action:hover {
  color: var(--brand-600);
  background: rgba(79, 70, 229, 0.1);
}
.col-action.active {
  color: #fff;
  background: var(--brand-600);
}
.action-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  box-sizing: border-box;
  border-radius: 999px;
  border: 1px solid #fff;
  background: var(--brand-600);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 12px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.col-action.active .action-badge {
  background: #fff;
  color: var(--brand-700);
  border-color: rgba(255, 255, 255, 0.9);
}

.filter-popover {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.filter-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.filter-option-list {
  max-height: 220px;
  overflow: auto;
  border-top: 1px solid var(--el-border-color-extra-light);
  border-bottom: 1px solid var(--el-border-color-extra-light);
  padding: 4px 0;
}
.filter-checkbox-group {
  display: flex;
  flex-direction: column;
}
.filter-option {
  width: 100%;
  height: auto;
  min-height: 30px;
  margin-right: 0;
  padding: 3px 4px;
  border-radius: var(--radius-sm);
  align-items: center;
}
.filter-option:hover {
  background: var(--el-fill-color-light);
}
.filter-option :deep(.el-checkbox__label) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.filter-option-text {
  display: inline-block;
  max-width: 184px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.filter-empty {
  padding: 18px 4px;
  color: var(--ink-500);
  font-size: 12px;
  text-align: center;
}
.filter-meta {
  color: var(--ink-500);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
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
.permission-cell {
  display: inline-flex;
  align-items: center;
  min-width: 0;
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
  .filter-actions {
    margin-left: 0;
  }
  .sort-field {
    max-width: 72px;
  }
}
</style>
