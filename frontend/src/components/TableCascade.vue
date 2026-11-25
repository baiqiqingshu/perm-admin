<template>
  <el-form-item label="项目">
    <el-select
      v-model="sel.projects"
      placeholder="全部项目"
      multiple
      clearable
      filterable
      collapse-tags
      collapse-tags-tooltip
      :max-collapse-tags="1"
      class="cascade-select project-select"
      @change="onSelectionChange"
    >
      <el-option
        v-for="p in projects"
        :key="p.code"
        :label="`${p.name}（${p.code}）`"
        :value="p.code"
      />
    </el-select>
  </el-form-item>
  <el-form-item label="环境">
    <el-select
      v-model="sel.environments"
      placeholder="全部环境"
      multiple
      clearable
      filterable
      collapse-tags
      collapse-tags-tooltip
      :max-collapse-tags="1"
      class="cascade-select env-select"
      @change="onSelectionChange"
    >
      <el-option
        v-for="e in envOptions"
        :key="e.code"
        :label="`${e.name}（${e.code}）`"
        :value="e.code"
      />
    </el-select>
  </el-form-item>
  <el-form-item label="数据库">
    <el-select
      v-model="sel.databases"
      placeholder="全部数据库"
      multiple
      clearable
      filterable
      collapse-tags
      collapse-tags-tooltip
      :max-collapse-tags="1"
      class="cascade-select db-select"
      @change="onSelectionChange"
    >
      <el-option
        v-for="d in dbOptions"
        :key="d.code"
        :label="`${d.name}（${d.code}）`"
        :value="d.code"
      />
    </el-select>
  </el-form-item>
  <el-form-item label="权限表">
    <el-select
      v-model="sel.tableCodes"
      placeholder="全部权限表"
      multiple
      clearable
      filterable
      collapse-tags
      collapse-tags-tooltip
      :max-collapse-tags="1"
      class="cascade-select table-select"
      @change="onSelectionChange"
    >
      <el-option
        v-for="t in tableOptions"
        :key="t.code"
        :label="`${t.name}（${t.tableName}）`"
        :value="t.code"
      />
    </el-select>
  </el-form-item>
</template>

<script setup>
import { reactive, computed, watch } from 'vue'
import { configState } from '../store/config'

const props = defineProps({
  // { projects: [], environments: [], databases: [], tableCodes: [] }
  modelValue: {
    type: Object,
    default: () => ({})
  }
})
const emit = defineEmits(['update:modelValue', 'change'])

const sel = reactive({ projects: [], environments: [], databases: [], tableCodes: [] })

const projects = computed(() => configState.projects || [])
const envOptions = computed(() => environmentOptionsFor(sel.projects))
const dbOptions = computed(() => databaseOptionsFor(sel.projects, sel.environments))
const tableOptions = computed(() => tableOptionsFor(sel.projects, sel.environments, sel.databases))

watch(
  () => props.modelValue,
  (value) => {
    const next = normalizeModel(value)
    if (!sameModel(next, sel)) {
      sel.projects = [...next.projects]
      sel.environments = [...next.environments]
      sel.databases = [...next.databases]
      sel.tableCodes = [...next.tableCodes]
      if (sanitizeSelections()) emitChange()
    }
  },
  { deep: true, immediate: true }
)

watch(
  projects,
  () => {
    if (sanitizeSelections()) emitChange()
  },
  { deep: true }
)

function asArray(value) {
  return Array.isArray(value) ? value.filter((v) => v !== null && v !== undefined && v !== '') : []
}

function uniqueValues(values) {
  return [...new Set(asArray(values))]
}

function normalizeModel(value) {
  const source = value || {}
  return {
    projects: uniqueValues(source.projects),
    environments: uniqueValues(source.environments),
    databases: uniqueValues(source.databases),
    tableCodes: uniqueValues(source.tableCodes)
  }
}

function arraysEqual(a, b) {
  if (a.length !== b.length) return false
  return a.every((v, i) => v === b[i])
}

function sameModel(a, b) {
  return (
    arraysEqual(a.projects, b.projects) &&
    arraysEqual(a.environments, b.environments) &&
    arraysEqual(a.databases, b.databases) &&
    arraysEqual(a.tableCodes, b.tableCodes)
  )
}

function dedupeByCode(items) {
  const seen = new Set()
  const result = []
  for (const item of items || []) {
    if (!item?.code || seen.has(item.code)) continue
    seen.add(item.code)
    result.push(item)
  }
  return result
}

function scopedProjects(projectCodes) {
  const codes = uniqueValues(projectCodes)
  if (!codes.length) return projects.value
  const wanted = new Set(codes)
  return projects.value.filter((p) => wanted.has(p.code))
}

function scopedEnvironmentNodes(projectCodes) {
  return scopedProjects(projectCodes).flatMap((p) => p.environments || [])
}

function environmentOptionsFor(projectCodes) {
  return dedupeByCode(scopedEnvironmentNodes(projectCodes))
}

function scopedDatabaseNodes(projectCodes, environmentCodes) {
  const environments = scopedEnvironmentNodes(projectCodes)
  const codes = uniqueValues(environmentCodes)
  const wanted = new Set(codes)
  const scopedEnvs = codes.length ? environments.filter((e) => wanted.has(e.code)) : environments
  return scopedEnvs.flatMap((e) => e.databases || [])
}

function databaseOptionsFor(projectCodes, environmentCodes) {
  return dedupeByCode(scopedDatabaseNodes(projectCodes, environmentCodes))
}

function tableOptionsFor(projectCodes, environmentCodes, databaseCodes) {
  const databases = scopedDatabaseNodes(projectCodes, environmentCodes)
  const codes = uniqueValues(databaseCodes)
  const wanted = new Set(codes)
  const scopedDbs = codes.length ? databases.filter((d) => wanted.has(d.code)) : databases
  return dedupeByCode(scopedDbs.flatMap((d) => d.permissionTables || []))
}

function selectedNodes(options, codes) {
  const wanted = new Set(uniqueValues(codes))
  return options.filter((item) => wanted.has(item.code))
}

function assignIfChanged(key, values) {
  const next = uniqueValues(values)
  if (arraysEqual(sel[key], next)) return false
  sel[key] = next
  return true
}

function sanitizeSelections() {
  if (!configState.loaded) return false
  const validProjects = new Set(projects.value.map((p) => p.code))
  const nextProjects = sel.projects.filter((code) => validProjects.has(code))

  const validEnvs = new Set(environmentOptionsFor(nextProjects).map((e) => e.code))
  const nextEnvironments = sel.environments.filter((code) => validEnvs.has(code))

  const validDbs = new Set(databaseOptionsFor(nextProjects, nextEnvironments).map((d) => d.code))
  const nextDatabases = sel.databases.filter((code) => validDbs.has(code))

  const validTables = new Set(
    tableOptionsFor(nextProjects, nextEnvironments, nextDatabases).map((t) => t.code)
  )
  const nextTableCodes = sel.tableCodes.filter((code) => validTables.has(code))

  let changed = assignIfChanged('projects', nextProjects)
  changed = assignIfChanged('environments', nextEnvironments) || changed
  changed = assignIfChanged('databases', nextDatabases) || changed
  changed = assignIfChanged('tableCodes', nextTableCodes) || changed
  return changed
}

function currentModel() {
  return normalizeModel(sel)
}

function emitChange() {
  const model = currentModel()
  emit('update:modelValue', model)
  emit('change', {
    ...model,
    projectNodes: selectedNodes(projects.value, sel.projects),
    envNodes: selectedNodes(envOptions.value, sel.environments),
    dbNodes: selectedNodes(dbOptions.value, sel.databases),
    tableNodes: selectedNodes(tableOptions.value, sel.tableCodes)
  })
}

function onSelectionChange() {
  sanitizeSelections()
  emitChange()
}
</script>

<style scoped>
.cascade-select {
  width: 210px;
}
.project-select {
  width: 220px;
}
.env-select {
  width: 180px;
}
.db-select {
  width: 210px;
}
.table-select {
  width: 230px;
}

@media (max-width: 720px) {
  .cascade-select,
  .project-select,
  .env-select,
  .db-select,
  .table-select {
    width: 100%;
  }
}
</style>
