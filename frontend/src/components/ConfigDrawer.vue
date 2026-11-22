<template>
  <el-drawer
    :model-value="modelValue"
    size="540px"
    class="config-drawer"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="ensureLoaded"
  >
    <template #header>
      <div class="drawer-head">
        <span class="drawer-title">当前配置</span>
        <span v-if="configState.loaded" class="muted">{{ statsText }}</span>
      </div>
    </template>

    <div class="drawer-toolbar">
      <el-button type="primary" plain :loading="reloading" @click="onReload">
        <el-icon><Refresh /></el-icon>&nbsp;重载配置
      </el-button>
      <span class="muted">来自 /api/config（密码已掩码）</span>
    </div>

    <el-tree
      v-loading="configState.loading"
      :data="treeData"
      :props="{ label: 'label', children: 'children' }"
      default-expand-all
      node-key="id"
      :expand-on-click-node="false"
      class="config-tree"
    >
      <template #default="{ data }">
        <span class="node">
          <span class="node-badge" :class="badgeClass(data.type)">{{ data.type }}</span>
          <span class="node-label">{{ data.label }}</span>
          <span v-if="data.detail" class="muted node-detail">{{ data.detail }}</span>
        </span>
      </template>
    </el-tree>
    <el-empty
      v-if="configState.loaded && treeData.length === 0"
      description="配置为空"
      :image-size="80"
    />
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { configState, loadConfig } from '../store/config'
import { reloadConfig } from '../api'

defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const reloading = ref(false)

function ensureLoaded() {
  loadConfig()
}

async function onReload() {
  reloading.value = true
  try {
    await reloadConfig()
    await loadConfig(true)
    ElMessage.success('配置已重载')
  } finally {
    reloading.value = false
  }
}

const statsText = computed(() => {
  let envs = 0
  let dbs = 0
  let tables = 0
  for (const p of configState.projects || []) {
    envs += (p.environments || []).length
    for (const e of p.environments || []) {
      dbs += (e.databases || []).length
      for (const d of e.databases || []) {
        tables += (d.permissionTables || []).length
      }
    }
  }
  return `${(configState.projects || []).length} 项目 · ${envs} 环境 · ${dbs} 库 · ${tables} 表`
})

const treeData = computed(() =>
  (configState.projects || []).map((p) => ({
    id: `p:${p.code}`,
    type: '项目',
    label: `${p.name}（${p.code}）`,
    children: (p.environments || []).map((e) => ({
      id: `p:${p.code}/e:${e.code}`,
      type: '环境',
      label: `${e.name}（${e.code}）`,
      children: (e.databases || []).map((d) => ({
        id: `p:${p.code}/e:${e.code}/d:${d.code}`,
        type: '数据库',
        label: `${d.name}（${d.code}）`,
        detail: d.jdbcUrl,
        children: (d.permissionTables || []).map((t) => ({
          id: `p:${p.code}/e:${e.code}/d:${d.code}/t:${t.code}`,
          type: '权限表',
          label: `${t.name}（${t.tableName}）`,
          detail: `权限 ${t.permissions?.length || 0} 项 · 范围 ${t.scopes?.length || 0} 项`
        }))
      }))
    }))
  }))
)

function badgeClass(type) {
  return { 项目: 'b-project', 环境: 'b-env', 数据库: 'b-db', 权限表: 'b-table' }[type] || 'b-table'
}
</script>

<style scoped>
.drawer-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.drawer-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-900);
}
.drawer-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.node {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  flex: 1;
  min-width: 0;
}
.node-badge {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 500;
  padding: 1px 7px;
  border-radius: 999px;
  line-height: 1.6;
}
.b-project {
  color: var(--brand-700);
  background: var(--brand-50);
  border: 1px solid var(--brand-100);
}
.b-env {
  color: #157a3b;
  background: #ebf7f0;
  border: 1px solid #c8e8d4;
}
.b-db {
  color: #92580a;
  background: #fdf3e3;
  border: 1px solid #f0dcb4;
}
.b-table {
  color: var(--ink-500);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}
.node-label {
  white-space: nowrap;
  color: var(--ink-700);
}
.node-detail {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
