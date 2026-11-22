<template>
  <div class="layout">
    <header class="topbar">
      <div class="topbar-inner">
        <div class="brand">
          <span class="brand-mark" aria-hidden="true">
            <el-icon :size="16"><Lock /></el-icon>
          </span>
          <div class="brand-text">
            <span class="brand-name">权限管理工具</span>
            <span class="brand-sub">Perm Admin</span>
          </div>
        </div>

        <nav class="nav" aria-label="主导航">
          <button
            v-for="item in navItems"
            :key="item.name"
            type="button"
            class="nav-item"
            :class="{ active: activeTab === item.name }"
            @click="activeTab = item.name"
          >
            <el-icon :size="15"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </button>
        </nav>

        <div class="actions">
          <el-tooltip content="重新加载配置" placement="bottom" :show-after="300">
            <button
              type="button"
              class="icon-btn"
              :class="{ spinning: configState.loading }"
              aria-label="重新加载配置"
              @click="onRefresh"
            >
              <el-icon :size="17"><Refresh /></el-icon>
            </button>
          </el-tooltip>
          <el-button @click="drawerVisible = true">
            <el-icon><Setting /></el-icon>&nbsp;查看配置
          </el-button>
        </div>
      </div>
    </header>

    <main class="main">
      <div class="main-inner">
        <PermissionQuery v-show="activeTab === 'query'" v-if="mountedTabs.query" />
        <PermissionAdd v-show="activeTab === 'add'" v-if="mountedTabs.add" />
      </div>
    </main>

    <ConfigDrawer v-model="drawerVisible" />
  </div>
</template>

<script setup>
import { reactive, ref, watch, onMounted } from 'vue'
import PermissionQuery from './views/PermissionQuery.vue'
import PermissionAdd from './views/PermissionAdd.vue'
import ConfigDrawer from './components/ConfigDrawer.vue'
import { configState, loadConfig } from './store/config'

const activeTab = ref('query')
const drawerVisible = ref(false)
const mountedTabs = reactive({ query: true, add: false })

const navItems = [
  { name: 'query', label: '权限查询', icon: 'Search' },
  { name: 'add', label: '新增权限', icon: 'CirclePlus' }
]

watch(activeTab, (tab) => {
  mountedTabs[tab] = true
})

onMounted(() => loadConfig())

function onRefresh() {
  loadConfig(true)
}
</script>

<style scoped>
.layout {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

/* 磨砂玻璃顶栏 */
.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: saturate(160%) blur(14px);
  -webkit-backdrop-filter: saturate(160%) blur(14px);
  border-bottom: 1px solid rgba(216, 222, 232, 0.7);
}
.topbar-inner {
  max-width: 1240px;
  margin: 0 auto;
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.brand-mark {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  /* 品牌区允许渐变 */
  background: linear-gradient(135deg, #6366f1 0%, #4338ca 100%);
  box-shadow: 0 4px 10px -4px rgba(79, 70, 229, 0.55),
    inset 0 1px 0 rgba(255, 255, 255, 0.22);
}
.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}
.brand-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-900);
  letter-spacing: 0.01em;
}
.brand-sub {
  font-size: 11px;
  color: var(--ink-500);
  letter-spacing: 0.04em;
}

/* 分段导航 */
.nav {
  display: flex;
  align-items: center;
  gap: 2px;
  background: rgba(237, 240, 246, 0.85);
  border: 1px solid rgba(216, 222, 232, 0.6);
  border-radius: 10px;
  padding: 3px;
}
.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  border-radius: 8px;
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  color: var(--ink-500);
  cursor: pointer;
  transition: color var(--dur) var(--ease), background-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease);
}
.nav-item:hover {
  color: var(--ink-900);
}
.nav-item.active {
  background: #fff;
  color: var(--brand-600);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1), 0 0 0 1px rgba(216, 222, 232, 0.5);
}
.nav-item:focus-visible {
  outline: 2px solid var(--brand-600);
  outline-offset: 1px;
}

.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.icon-btn {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--ink-500);
  cursor: pointer;
  transition: background-color var(--dur) var(--ease), color var(--dur) var(--ease);
}
.icon-btn:hover {
  background: var(--el-fill-color-light);
  color: var(--ink-900);
}
.icon-btn.spinning .el-icon {
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.main {
  flex: 1;
  padding: 20px 24px 40px;
}
.main-inner {
  max-width: 1240px;
  margin: 0 auto;
}

@media (max-width: 720px) {
  .brand-sub {
    display: none;
  }
  .topbar-inner {
    gap: 12px;
    padding: 0 16px;
  }
  .main {
    padding: 16px 12px 32px;
  }
}
</style>
