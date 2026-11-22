<template>
  <el-form-item label="项目">
    <el-select
      v-model="sel.project"
      placeholder="项目"
      clearable
      filterable
      style="width: 170px"
      @change="onProjectChange"
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
      v-model="sel.environment"
      placeholder="环境"
      clearable
      filterable
      :disabled="!sel.project"
      style="width: 150px"
      @change="onEnvironmentChange"
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
      v-model="sel.database"
      placeholder="数据库"
      clearable
      filterable
      :disabled="!sel.environment"
      style="width: 170px"
      @change="onDatabaseChange"
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
      v-model="sel.tableCode"
      placeholder="权限表"
      clearable
      filterable
      :disabled="!sel.database"
      style="width: 190px"
      @change="emitChange"
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
  // { project, environment, database, tableCode }
  modelValue: {
    type: Object,
    default: () => ({})
  }
})
const emit = defineEmits(['update:modelValue', 'change'])

const sel = reactive({ project: '', environment: '', database: '', tableCode: '' })

watch(
  () => props.modelValue,
  (v) => {
    const next = v || {}
    if (
      next.project !== sel.project ||
      next.environment !== sel.environment ||
      next.database !== sel.database ||
      next.tableCode !== sel.tableCode
    ) {
      sel.project = next.project || ''
      sel.environment = next.environment || ''
      sel.database = next.database || ''
      sel.tableCode = next.tableCode || ''
    }
  },
  { deep: true, immediate: true }
)

const projects = computed(() => configState.projects || [])
const projectNode = computed(() => projects.value.find((p) => p.code === sel.project))
const envOptions = computed(() => projectNode.value?.environments || [])
const envNode = computed(() => envOptions.value.find((e) => e.code === sel.environment))
const dbOptions = computed(() => envNode.value?.databases || [])
const dbNode = computed(() => dbOptions.value.find((d) => d.code === sel.database))
const tableOptions = computed(() => dbNode.value?.permissionTables || [])
const tableNode = computed(() => tableOptions.value.find((t) => t.code === sel.tableCode))

function emitChange() {
  emit('update:modelValue', { ...sel })
  emit('change', {
    ...sel,
    projectNode: projectNode.value || null,
    envNode: envNode.value || null,
    dbNode: dbNode.value || null,
    tableNode: tableNode.value || null
  })
}

function onProjectChange() {
  sel.environment = ''
  sel.database = ''
  sel.tableCode = ''
  emitChange()
}

function onEnvironmentChange() {
  sel.database = ''
  sel.tableCode = ''
  emitChange()
}

function onDatabaseChange() {
  sel.tableCode = ''
  emitChange()
}
</script>
