<template>
  <el-dialog
    :model-value="modelValue"
    width="780px"
    :close-on-click-modal="false"
    class="sql-preview-dialog"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="dlg-head">
        <div class="dlg-title-row">
          <span class="dlg-title">SQL 预览</span>
          <span v-if="groups?.length" class="dlg-sub muted">
            {{ groups.length }} 组 · 共 {{ totalStatements }} 条语句
          </span>
        </div>
        <div class="dlg-desc muted">确认无误后执行；每组一个事务，失败不回滚其他组</div>
      </div>
    </template>

    <div class="dlg-alerts">
      <!-- 去重跳过 -->
      <el-alert v-if="skipped?.length" type="warning" :closable="false" class="notice">
        <template #title>
          <div class="notice-title">
            <span>已存在 {{ skipped.length }} 条权限将跳过（不重复插入）</span>
            <button type="button" class="notice-toggle" @click="showSkipped = !showSkipped">
              {{ showSkipped ? '收起明细' : `查看明细` }}
              <el-icon :size="12" class="toggle-icon" :class="{ open: showSkipped }">
                <ArrowDown />
              </el-icon>
            </button>
          </div>
        </template>
        <el-collapse-transition>
          <div v-show="showSkipped" class="notice-detail">
            <div class="detail-table">
              <div class="detail-row detail-head">
                <span>工号</span><span>权限</span><span>数据范围</span><span>目标表</span>
              </div>
              <div v-for="(s, i) in skipped" :key="i" class="detail-row">
                <span class="mono">{{ s.personId }}</span>
                <span>{{ s.permissionLabel }}</span>
                <span>{{ s.scopeLabel }}</span>
                <span class="mono dim">{{ s.tableName }}<em class="dim"> @ {{ s.dbCode }}</em></span>
              </div>
            </div>
          </div>
        </el-collapse-transition>
      </el-alert>

      <!-- 部分表不支持所选项 -->
      <el-alert v-if="unsupported?.length" type="info" :closable="false" class="notice">
        <template #title>
          <div class="notice-title">
            <span>{{ unsupported.length }} 项选择不被部分表支持，已在这些表上跳过</span>
            <button type="button" class="notice-toggle" @click="showUnsupported = !showUnsupported">
              {{ showUnsupported ? '收起明细' : `查看明细` }}
              <el-icon :size="12" class="toggle-icon" :class="{ open: showUnsupported }">
                <ArrowDown />
              </el-icon>
            </button>
          </div>
        </template>
        <el-collapse-transition>
          <div v-show="showUnsupported" class="notice-detail">
            <div class="unsupported-list">
              <span v-for="(u, i) in unsupported" :key="i" class="unsupported-item">
                {{ u.tableName }}<em class="dim"> @ {{ u.dbCode }}</em>
                不支持{{ u.type === 'permission' ? '权限' : '数据范围' }}「{{ u.label }}」
              </span>
            </div>
          </div>
        </el-collapse-transition>
      </el-alert>

      <!-- 去重查询失败 -->
      <el-alert v-if="errors?.length" type="error" :closable="false" class="notice">
        <template #title>
          <div class="notice-title">
            <span>{{ errors.length }} 张表去重检查失败，对应数据未做去重</span>
            <button type="button" class="notice-toggle" @click="showErrors = !showErrors">
              {{ showErrors ? '收起明细' : `查看明细` }}
              <el-icon :size="12" class="toggle-icon" :class="{ open: showErrors }">
                <ArrowDown />
              </el-icon>
            </button>
          </div>
        </template>
        <el-collapse-transition>
          <div v-show="showErrors" class="notice-detail">
            <div class="unsupported-list">
              <span v-for="(e, i) in errors" :key="i" class="unsupported-item">
                {{ e.tableName }}<em class="dim"> @ {{ e.dbCode }}</em>：{{ e.error }}
              </span>
            </div>
          </div>
        </el-collapse-transition>
      </el-alert>
    </div>

    <el-empty
      v-if="!groups || groups.length === 0"
      description="没有需要执行的 SQL"
      :image-size="72"
    />
    <el-tabs v-else type="border-card" class="preview-tabs">
      <el-tab-pane v-for="g in groups" :key="g.key">
        <template #label>
          <span class="tab-label">
            {{ g.dbName }}<span class="tab-table mono">{{ g.tableName }}</span>
          </span>
        </template>
        <div class="jdbc muted mono">{{ g.jdbcUrl }}</div>
        <div class="sql-list">
          <pre
            v-for="(s, i) in (g.statements || []).slice(0, MAX_SHOW)"
            :key="i"
            class="sql-line"
          ><code>{{ i + 1 }}. {{ s.sql }}</code></pre>
        </div>
        <el-alert
          v-if="(g.statements || []).length > MAX_SHOW"
          type="info"
          :closable="false"
          :title="`共 ${g.statements.length} 条，仅展示前 ${MAX_SHOW} 条（执行时将提交全部）`"
          class="more-tip"
        />
        <div class="muted count-tip">本组共 {{ (g.statements || []).length }} 条语句</div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <div class="dlg-footer">
        <span class="muted footer-hint">
          <template v-if="skipped?.length">执行仅写入 {{ totalStatements }} 条新记录</template>
        </span>
        <div class="footer-btns">
          <el-button :disabled="executing" @click="emit('update:modelValue', false)">
            取消
          </el-button>
          <el-button
            type="primary"
            :loading="executing"
            :disabled="!groups || groups.length === 0"
            @click="emit('confirm', groups)"
          >
            确定执行
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'

const MAX_SHOW = 50

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  groups: { type: Array, default: () => [] },
  skipped: { type: Array, default: () => [] },
  unsupported: { type: Array, default: () => [] },
  errors: { type: Array, default: () => [] },
  executing: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const showSkipped = ref(false)
const showUnsupported = ref(false)
const showErrors = ref(false)

const totalStatements = computed(() =>
  (props.groups || []).reduce((n, g) => n + (g.statements || []).length, 0)
)
</script>

<style scoped>
.dlg-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-right: 40px;
}
.dlg-title-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.dlg-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-900);
}
.dlg-sub {
  font-variant-numeric: tabular-nums;
}

.dlg-alerts {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}
.dlg-alerts:empty {
  margin-bottom: 0;
}
.notice :deep(.el-alert__content) {
  width: 100%;
}
.notice-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}
.notice-toggle {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  padding: 2px 4px;
  font-size: 12px;
  font-family: inherit;
  color: inherit;
  opacity: 0.85;
  cursor: pointer;
  border-radius: 4px;
  transition: opacity var(--dur) var(--ease);
}
.notice-toggle:hover {
  opacity: 1;
}
.toggle-icon {
  transition: transform 0.2s var(--ease);
}
.toggle-icon.open {
  transform: rotate(180deg);
}

.notice-detail {
  margin-top: 8px;
  border-top: 1px dashed rgba(0, 0, 0, 0.1);
  padding-top: 8px;
}
.detail-table {
  max-height: 180px;
  overflow: auto;
  font-size: 12px;
}
.detail-row {
  display: grid;
  grid-template-columns: 90px 80px 90px 1fr;
  gap: 12px;
  padding: 3px 0;
  align-items: baseline;
}
.detail-head {
  font-weight: 600;
  opacity: 0.75;
}
.detail-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dim {
  opacity: 0.65;
  font-style: normal;
  font-weight: 400;
}
.unsupported-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  max-height: 140px;
  overflow: auto;
  font-size: 12px;
}
.unsupported-item {
  font-weight: 400;
}

.preview-tabs {
  margin-top: 2px;
}
.tab-label {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
}
.tab-table {
  font-size: 11px;
  opacity: 0.6;
}
.jdbc {
  margin-bottom: 8px;
  word-break: break-all;
  font-size: 12px;
}
.sql-list {
  max-height: 360px;
  overflow: auto;
  background: #f8f9fb;
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 8px;
  padding: 6px 12px;
}
.sql-line {
  margin: 6px 0;
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--ink-700);
}
.more-tip {
  margin-top: 8px;
}
.count-tip {
  margin-top: 8px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.dlg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.footer-btns {
  display: flex;
  gap: 10px;
}
.footer-btns .el-button + .el-button {
  margin-left: 0;
}
</style>
