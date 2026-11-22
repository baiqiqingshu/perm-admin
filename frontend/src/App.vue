<template>
  <div class="layout">
    <header class="header">
      <div class="title">
        <el-icon :size="20"><Lock /></el-icon>
        <span>权限管理工具</span>
      </div>
      <el-button @click="drawerVisible = true">
        <el-icon><Setting /></el-icon>&nbsp;配置
      </el-button>
    </header>
    <main class="main">
      <el-tabs v-model="activeTab" class="main-tabs">
        <el-tab-pane label="权限查询" name="query">
          <PermissionQuery v-if="mountedTabs.query" />
        </el-tab-pane>
        <el-tab-pane label="新增权限" name="add">
          <PermissionAdd v-if="mountedTabs.add" />
        </el-tab-pane>
      </el-tabs>
    </main>
    <ConfigDrawer v-model="drawerVisible" />
  </div>
</template>

<script setup>
import { reactive, ref, watch, onMounted } from 'vue'
import PermissionQuery from './views/PermissionQuery.vue'
import PermissionAdd from './views/PermissionAdd.vue'
import ConfigDrawer from './components/ConfigDrawer.vue'
import { loadConfig } from './store/config'

const activeTab = ref('query')
const drawerVisible = ref(false)
const mountedTabs = reactive({ query: true, add: false })

watch(activeTab, (tab) => {
  mountedTabs[tab] = true
})

onMounted(() => loadConfig())
</script>

<style scoped>
.layout {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}
.main {
  flex: 1;
  padding: 16px 20px 28px;
}
.main-tabs :deep(.el-tabs__content) {
  overflow: visible;
}
</style>
