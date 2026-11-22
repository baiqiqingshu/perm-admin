<template>
  <el-drawer
    :model-value="modelValue"
    title="当前配置"
    size="520px"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="ensureLoaded"
  >
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
    >
      <template #default="{ data }">
        <span class="node">
          <el-tag size="small" :type="tagType(data.type)" effect="plain">{{ data.type }}</el-tag>
          <span class="node-label">{{ data.label }}</span>
          <span v-if="data.detail" class="muted node-detail">{{ data.detail }}</span>
        </span>
      </template>
    </el-tree>
    <el-empty
      v-if="configState.loaded && treeData.length === 0"
      description="配置为空"
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
          detail: `权限 ${t.permissions?.length || 0} 项，范围 ${t.scopes?.length || 0} 项`
        }))
      }))
    }))
  }))
)

function tagType(type) {
  return { 项目: 'primary', 环境: 'success', 数据库: 'warning', 权限表: 'info' }[type] || 'info'
}
</script>

<style scoped>
.drawer-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.node {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}
.node-label {
  white-space: nowrap;
}
.node-detail {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
