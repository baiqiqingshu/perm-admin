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

      <div v-loading="loading" class="result-grid">
        <el-auto-resizer @resize="onGridResize">
          <template #default="{ height, width }">
            <el-table-v2
              :columns="tableColumns"
              :data="visibleRows"
              :width="width"
              :height="height"
              :header-height="40"
              :row-height="40"
              :row-class="rowClass"
              row-key="_rowKey"
              fixed
            >
              <template #header-cell="{ column }">
                <el-checkbox
                  v-if="column.kind === 'selection'"
                  :model-value="allVisibleSelected"
                  :indeterminate="someVisibleSelected"
                  :disabled="!visibleRows.length"
                  aria-label="全选当前结果"
                  @change="toggleAllVisible"
                />
                <span v-else-if="column.kind !== 'data'" class="grid-head-title">{{ column.title }}</span>
                <div v-else class="column-head">
                  <span class="column-title" :title="column.title">{{ column.title }}</span>
                  <span class="column-actions">
                    <button
                      type="button"
                      class="col-action"
                      :class="{ active: sortIndexFor(column.key) !== -1 }"
                      :title="sortButtonTitle(column.col)"
                      :aria-label="sortButtonTitle(column.col)"
                      @click="toggleSort(column.key)"
                    >
                      <el-icon>
                        <SortUp v-if="sortDirectionFor(column.key) === 'asc'" />
                        <SortDown v-else-if="sortDirectionFor(column.key) === 'desc'" />
                        <Sort v-else />
                      </el-icon>
                      <span v-if="sortIndexFor(column.key) !== -1" class="action-badge">
                        {{ sortIndexFor(column.key) + 1 }}
                      </span>
                    </button>
                    <el-popover
                      placement="bottom-start"
                      :width="260"
                      trigger="click"
                      @show="onFilterPopoverShow(column.key)"
                    >
                      <template #reference>
                        <button
                          type="button"
                          class="col-action"
                          :class="{ active: isFilterActive(column.key) }"
                          :title="`筛选${column.title}`"
                          :aria-label="`筛选${column.title}`"
                        >
                          <el-icon><Filter /></el-icon>
                          <span v-if="activeFilterCount(column.key)" class="action-badge">
                            {{ activeFilterCount(column.key) }}
                          </span>
                        </button>
                      </template>
                      <div class="filter-popover">
                        <el-input
                          v-model="filterSearch[column.key]"
                          size="small"
                          clearable
                          :placeholder="`搜索${column.title}`"
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
                            :disabled="!visibleFilterOptions(column.key).length || allVisibleOptionsSelected(column.key)"
                            @click="selectVisibleFilterOptions(column.key)"
                          >
                            全选当前搜索
                          </el-button>
                          <el-button
                            link
                            size="small"
                            :disabled="!activeFilterCount(column.key)"
                            @click="clearColumnFilter(column.key)"
                          >
                            清空
                          </el-button>
                        </div>
                        <div class="filter-option-list">
                          <el-checkbox-group
                            v-if="visibleFilterOptions(column.key).length"
                            v-model="headerFilters[column.key]"
                            class="filter-checkbox-group"
                          >
                            <el-checkbox
                              v-for="(opt, optIndex) in visibleFilterOptions(column.key)"
                              :key="`${column.key}-${optIndex}-${opt.value}`"
                              :value="opt.value"
                              class="filter-option"
                            >
                              <span class="filter-option-text" :title="opt.label">{{ opt.label }}</span>
                            </el-checkbox>
                          </el-checkbox-group>
                          <div v-else class="filter-empty">{{ filterEmptyText(column.key) }}</div>
                        </div>
                        <div class="filter-meta">
                          已选 {{ activeFilterCount(column.key) }} / 共 {{ filterOptionTotal(column.key) }} 项
                        </div>
                      </div>
                    </el-popover>
                  </span>
                  <span
                    class="col-resizer"
                    :class="{ 'is-active': resizingKey === column.key }"
                    role="separator"
                    aria-orientation="vertical"
                    :title="`拖动调整${column.title}列宽，双击还原`"
                    @pointerdown="startResize($event, column.key)"
                    @click.stop
                    @dblclick.stop="resetColumnWidth(column.key)"
                  ></span>
                </div>
              </template>
              <template #cell="{ column, rowData, rowIndex }">
                <el-checkbox
                  v-if="column.kind === 'selection'"
                  :model-value="selectedKeys.has(rowData._rowKey)"
                  :aria-label="`选择第 ${rowIndex + 1} 行`"
                  @change="(checked) => toggleRow(rowData._rowKey, checked)"
                />
                <span v-else-if="column.kind === 'index'" class="grid-index">{{ rowIndex + 1 }}</span>
                <el-button
                  v-else-if="column.kind === 'operation'"
                  type="danger"
                  link
                  size="small"
                  @click="onRowDelete(rowData)"
                >
                  删除
                </el-button>
                <span v-else-if="column.col.type === 'scope'" class="grid-text" :title="displayScopeText(rowData)">
                  {{ rowData.scopeLabel || '—' }}<span v-if="!isBlank(rowData.scopeValue)" class="grid-sub">（{{ rowData.scopeValue }}）</span>
                </span>
                <span v-else-if="column.col.type === 'permission'" class="permission-cell" :title="displayPermissionText(rowData)">
                  <el-tag size="small" effect="plain" class="perm-tag">
                    {{ rowData.permissionLabel || rowData.permissionValue || '—' }}
                  </el-tag>
                  <span v-if="!isBlank(rowData.permissionValue)" class="grid-sub grid-text">（{{ rowData.permissionValue }}）</span>
                </span>
                <span v-else class="grid-text" :title="displayCell(rowData, column.col)">{{ displayCell(rowData, column.col) }}</span>
              </template>
              <template #empty>
                <div class="grid-empty">
                  <span class="grid-empty-icon"><el-icon :size="20"><Document /></el-icon></span>
                  <p class="grid-empty-title">{{ rows.length ? '没有符合表头筛选的记录' : '暂无查询结果' }}</p>
                  <p class="grid-empty-hint">
                    {{ rows.length ? '放宽或清空表头筛选后即可看到结果。' : '设置上方筛选条件后点击「查询」。' }}
                  </p>
                  <el-button v-if="rows.length" size="small" @click="clearAllHeaderFilters">清空表头筛选</el-button>
                </div>
              </template>
            </el-table-v2>
          </template>
        </el-auto-resizer>
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
import { reactive, ref, shallowRef, computed, onMounted, onBeforeUnmount } from 'vue'
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

const rows = shallowRef([])
const sortRules = ref([])

function displayScopeText(row) {
  return `${row.scopeLabel ?? ''}（${row.scopeValue ?? ''}）`
}

function displayPermissionText(row) {
  return `${row.permissionLabel ?? ''}（${row.permissionValue ?? ''}）`
}

const dataColumns = [
  { key: 'personId', label: '工号', width: 122, minWidth: 96, maxWidth: 200, flexGrow: 0.5, type: 'text', getValue: (row) => row.personId ?? '' },
  { key: 'personName', label: '用户名称', width: 132, minWidth: 100, maxWidth: 240, flexGrow: 0.7, type: 'text', getValue: (row) => row.personName ?? '' },
  { key: 'projectName', label: '项目', width: 138, minWidth: 100, maxWidth: 280, flexGrow: 0.9, type: 'text', getValue: (row) => row.projectName ?? '' },
  { key: 'environmentName', label: '环境', width: 104, minWidth: 84, maxWidth: 180, flexGrow: 0.4, type: 'text', getValue: (row) => row.environmentName ?? '' },
  { key: 'databaseName', label: '数据库', width: 150, minWidth: 110, maxWidth: 300, flexGrow: 1, type: 'text', getValue: (row) => row.databaseName ?? '' },
  { key: 'tableName', label: '权限表', width: 196, minWidth: 130, maxWidth: 400, flexGrow: 1.3, type: 'text', getValue: (row) => row.tableName ?? '' },
  { key: 'scope', label: '数据范围', width: 220, minWidth: 140, maxWidth: 460, flexGrow: 1.5, type: 'scope', getValue: displayScopeText },
  { key: 'permission', label: '权限', width: 196, minWidth: 130, maxWidth: 380, flexGrow: 1.1, type: 'permission', getValue: displayPermissionText }
]
const columnMap = Object.fromEntries(dataColumns.map((col) => [col.key, col]))
const SELECTION_WIDTH = 46
const INDEX_WIDTH = 58
const OPERATION_WIDTH = 80
const SCROLLBAR_SIZE = 6
const RESIZE_MAX_WIDTH = 720
const gridWidth = ref(0)
const columnWidths = reactive(Object.fromEntries(dataColumns.map((col) => [col.key, col.width])))
const resizedKeys = reactive(new Set())
const resizingKey = ref('')
let resizeState = null

const layoutWidths = computed(() => {
  const widths = Object.fromEntries(dataColumns.map((col) => [col.key, columnWidths[col.key]]))
  const fixedWidth = SELECTION_WIDTH + INDEX_WIDTH + OPERATION_WIDTH + SCROLLBAR_SIZE
  let extra = gridWidth.value - fixedWidth - Object.values(widths).reduce((sum, w) => sum + w, 0)
  let pool = dataColumns.filter((col) => !resizedKeys.has(col.key) && widths[col.key] < col.maxWidth)
  while (extra > 0.5 && pool.length) {
    const totalGrow = pool.reduce((sum, col) => sum + col.flexGrow, 0)
    let used = 0
    for (const col of pool) {
      const add = Math.min((extra * col.flexGrow) / totalGrow, col.maxWidth - widths[col.key])
      widths[col.key] += add
      used += add
    }
    extra -= used
    pool = pool.filter((col) => widths[col.key] < col.maxWidth)
  }
  return widths
})

const tableColumns = computed(() => [
  { key: '__selection', kind: 'selection', width: SELECTION_WIDTH, fixed: 'left', align: 'center', class: 'grid-col-fixed', headerClass: 'grid-col-fixed' },
  { key: '__index', kind: 'index', title: '#', width: INDEX_WIDTH, fixed: 'left', align: 'center', class: 'grid-col-fixed', headerClass: 'grid-col-fixed' },
  ...dataColumns.map((col, i) => {
    const edge = i === dataColumns.length - 1 ? 'grid-col-last' : ''
    return { key: col.key, dataKey: col.key, kind: 'data', col, title: col.label, width: layoutWidths.value[col.key], minWidth: col.minWidth, maxWidth: col.maxWidth, flexGrow: 0, flexShrink: 0, class: edge, headerClass: edge }
  }),
  { key: '__operation', kind: 'operation', title: '操作', width: OPERATION_WIDTH, fixed: 'right', align: 'center', class: 'grid-col-operation', headerClass: 'grid-col-operation' }
])
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
const selectedKeys = shallowRef(new Set())
const selection = computed(() => visibleRows.value.filter((row) => selectedKeys.value.has(row._rowKey)))
const allVisibleSelected = computed(() => visibleRows.value.length > 0 && selection.value.length === visibleRows.value.length)
const someVisibleSelected = computed(() => selection.value.length > 0 && !allVisibleSelected.value)

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
onBeforeUnmount(stopResize)

function isBlank(value) {
  return value === null || value === undefined || value === ''
}

function withRowKeys(list) {
  const seen = new Map()
  return list.map((row) => {
    const base = [row.project, row.environment, row.database, row.tableCode, row.personId, row.scopeValue, row.permissionValue]
      .map((v) => encodeURIComponent(v ?? ''))
      .join('|')
    const n = seen.get(base) || 0
    seen.set(base, n + 1)
    return { ...row, _rowKey: n ? `${base}#${n}` : base }
  })
}

function rowClass({ rowData, rowIndex }) {
  return [rowIndex % 2 ? 'is-striped' : '', selectedKeys.value.has(rowData._rowKey) ? 'is-selected' : ''].join(' ')
}

function toggleRow(key, checked) {
  const next = new Set(selectedKeys.value)
  if (checked) next.add(key)
  else next.delete(key)
  selectedKeys.value = next
}

function toggleAllVisible(checked) {
  const next = new Set(selectedKeys.value)
  for (const row of visibleRows.value) {
    if (checked) next.add(row._rowKey)
    else next.delete(row._rowKey)
  }
  selectedKeys.value = next
}

function clearSelection() {
  selectedKeys.value = new Set()
}

function onGridResize({ width }) {
  gridWidth.value = width
}

function startResize(event, key) {
  if (event.button !== 0) return
  event.preventDefault()
  event.stopPropagation()
  stopResize()
  resizeState = { key, startX: event.clientX, startWidth: layoutWidths.value[key] }
  columnWidths[key] = resizeState.startWidth
  resizedKeys.add(key)
  resizingKey.value = key
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('pointermove', onResizeMove)
  document.addEventListener('pointerup', stopResize)
  document.addEventListener('pointercancel', stopResize)
}

function onResizeMove(event) {
  if (!resizeState) return
  const { key, startX, startWidth } = resizeState
  const next = startWidth + event.clientX - startX
  columnWidths[key] = Math.round(Math.min(Math.max(next, columnMap[key].minWidth), RESIZE_MAX_WIDTH))
}

function stopResize() {
  document.removeEventListener('pointermove', onResizeMove)
  document.removeEventListener('pointerup', stopResize)
  document.removeEventListener('pointercancel', stopResize)
  if (!resizeState) return
  resizeState = null
  resizingKey.value = ''
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

function resetColumnWidth(key) {
  columnWidths[key] = columnMap[key].width
  resizedKeys.delete(key)
}

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
    rows.value = withRowKeys(data?.rows || [])
    errors.value = data?.errors || []
    clearSelection()
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
  clearSelection()
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

.result-grid {
  height: clamp(420px, calc(100vh - 375px), 760px);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  font-variant-numeric: tabular-nums;
}
.result-grid :deep(.el-table-v2) {
  --grid-line: #edf0f4;
  --el-table-row-hover-bg-color: rgba(79, 70, 229, 0.045);
  font-size: 13px;
  color: var(--ink-700);
}
.result-grid :deep(.el-table-v2__header-row) {
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.result-grid :deep(.el-table-v2__header-cell) {
  position: relative;
  padding: 0 10px;
  background: #f8fafc;
  color: var(--ink-700);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  border-right: 1px solid var(--grid-line);
}
.result-grid :deep(.el-table-v2__row) {
  border-bottom: 1px solid var(--grid-line);
  transition: background-color var(--dur) var(--ease);
}
.result-grid :deep(.el-table-v2__row.is-striped) {
  background: #fafbfc;
}
.result-grid :deep(.el-table-v2__row.is-hovered),
.result-grid :deep(.el-table-v2__row:hover) {
  background: rgba(79, 70, 229, 0.045);
}
.result-grid :deep(.el-table-v2__row.is-selected) {
  background: rgba(79, 70, 229, 0.075);
}
.result-grid :deep(.el-table-v2__row.is-selected.is-hovered),
.result-grid :deep(.el-table-v2__row.is-selected:hover) {
  background: rgba(79, 70, 229, 0.11);
}
.result-grid :deep(.el-table-v2__row-cell) {
  padding: 0 10px;
  border-right: 1px solid var(--grid-line);
}
.result-grid :deep(.grid-col-fixed) {
  padding: 0 6px;
}
.result-grid :deep(.el-table-v2__row-cell.grid-col-last),
.result-grid :deep(.el-table-v2__header-cell.grid-col-last) {
  border-right: 0;
}
.result-grid :deep(.el-table-v2__row-cell.grid-col-operation),
.result-grid :deep(.el-table-v2__header-cell.grid-col-operation) {
  border-right: 0;
  border-left: 1px solid var(--grid-line);
}
.result-grid :deep(.el-table-v2__left) {
  box-shadow: 4px 0 8px -6px rgba(15, 23, 42, 0.14);
}
.result-grid :deep(.el-table-v2__right) {
  box-shadow: -4px 0 8px -6px rgba(15, 23, 42, 0.14);
}
.result-grid :deep(.el-table-v2__empty) {
  display: flex;
  align-items: center;
  justify-content: center;
}
.result-grid :deep(.el-checkbox) {
  height: auto;
}
.grid-head-title {
  color: var(--ink-500);
}
.grid-index {
  color: var(--ink-500);
  font-size: 12px;
}
.grid-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.grid-sub {
  color: var(--ink-500);
  font-size: 12px;
}
.grid-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 24px;
  text-align: center;
}
.grid-empty-icon {
  width: 40px;
  height: 40px;
  margin-bottom: 6px;
  border-radius: var(--radius-md);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-600);
  background: var(--brand-50);
}
.grid-empty-title {
  margin: 0;
  color: var(--ink-900);
  font-size: 14px;
  font-weight: 600;
}
.grid-empty-hint {
  margin: 0 0 6px;
  color: var(--ink-500);
  font-size: 12px;
}
.col-resizer {
  position: absolute;
  top: 0;
  right: -1px;
  bottom: 0;
  z-index: 1;
  width: 8px;
  cursor: col-resize;
  touch-action: none;
}
.col-resizer::after {
  content: '';
  position: absolute;
  top: 10px;
  bottom: 10px;
  right: 3px;
  width: 2px;
  border-radius: 1px;
  background: transparent;
  transition: background-color var(--dur) var(--ease), top var(--dur) var(--ease), bottom var(--dur) var(--ease);
}
.col-resizer:hover::after,
.col-resizer.is-active::after {
  top: 0;
  bottom: 0;
  background: var(--brand-500);
}
.col-resizer.is-active::after {
  background: var(--brand-600);
}

.column-head {
  flex: 1;
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
.col-action:focus-visible,
.chip-action:focus-visible {
  outline: 2px solid var(--brand-500);
  outline-offset: 1px;
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
  box-sizing: border-box;
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
  flex-shrink: 0;
  height: 20px;
  margin-right: 4px;
  padding: 0 6px;
  font-size: 12px;
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
