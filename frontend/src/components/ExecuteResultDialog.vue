<template>
  <el-dialog
    :model-value="modelValue"
    width="720px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    class="execute-result-dialog"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <template #header>
      <div class="dlg-head">
        <div class="dlg-title-row">
          <span class="dlg-title">执行结果</span>
          <span v-if="merged.length" class="dlg-sub muted">
            {{ merged.length }} 组 · 共提交 {{ submitted }} 条
          </span>
        </div>
        <div class="dlg-desc muted">每组一个事务独立执行；以下为本批次全部执行结果</div>
      </div>
    </template>

    <el-empty v-if="isEmpty" description="没有执行任何 SQL" :image-size="72" />
    <template v-else>
      <!-- 全部成功且无跳过/不支持：简洁成功态 -->
      <div v-if="isAllClean" class="success-hero">
        <span class="hero-icon">
          <el-icon :size="36"><CircleCheckFilled /></el-icon>
        </span>
        <div class="hero-title">全部执行成功</div>
        <div class="hero-sub muted">
          共提交 {{ submitted }} 条 · 成功 {{ okRows }} 条 · {{ okItems.length }} 组
        </div>
      </div>

      <!-- 有失败/跳过：顶部汇总条 -->
      <div v-else class="stat-row">
        <span class="stat-chip">共提交 <b>{{ submitted }}</b> 条</span>
        <span v-if="okItems.length" class="stat-chip ok">
          <el-icon :size="12"><CircleCheckFilled /></el-icon>
          成功 <b>{{ okRows }}</b> 条
        </span>
        <span v-if="failItems.length" class="stat-chip bad">
          <el-icon :size="12"><CircleCloseFilled /></el-icon>
          失败 <b>{{ failItems.length }}</b> 组
        </span>
        <span v-if="skipped.length" class="stat-chip warn">
          <el-icon :size="12"><WarningFilled /></el-icon>
          跳过 <b>{{ skipped.length }}</b> 条
        </span>
        <span v-if="unsupported.length" class="stat-chip info">
          <el-icon :size="12"><InfoFilled /></el-icon>
          不支持 <b>{{ unsupported.length }}</b> 项
        </span>
        <span v-if="errors.length" class="stat-chip warn">
          <el-icon :size="12"><WarningFilled /></el-icon>
          去重检查异常 <b>{{ errors.length }}</b> 项
        </span>
      </div>

      <!-- 明细分区 -->
      <div class="sections">
        <!-- 失败组 -->
        <section v-if="failItems.length" class="res-section bad">
          <header class="sec-head">
            <el-icon :size="14"><CircleCloseFilled /></el-icon>
            <span>执行失败 · {{ failItems.length }} 组</span>
          </header>
          <div class="sec-body">
            <div v-for="it in failItems" :key="it.key" class="res-item">
              <div class="res-item-main">
                <span class="res-target">
                  {{ it.dbName || it.dbCode || it.key
                  }}<span v-if="it.tableName" class="res-table mono">（{{ it.tableName }}）</span>
                </span>
                <el-tag type="danger" size="small" effect="plain" class="res-tag">失败</el-tag>
              </div>
              <div class="res-error">{{ it.error || '未知错误' }}</div>
            </div>
          </div>
        </section>

        <!-- 成功组 -->
        <section v-if="okItems.length" class="res-section ok">
          <header class="sec-head">
            <el-icon :size="14"><CircleCheckFilled /></el-icon>
            <span>执行成功 · {{ okItems.length }} 组</span>
          </header>
          <div class="sec-body">
            <div v-for="it in okItems" :key="it.key" class="res-item row">
              <span class="res-target">
                {{ it.dbName || it.dbCode || it.key
                }}<span v-if="it.tableName" class="res-table mono">（{{ it.tableName }}）</span>
              </span>
              <span class="res-count mono">{{ it.rows ?? it.stmtCount ?? 0 }} 条</span>
              <el-tag type="success" size="small" effect="plain" class="res-tag">成功</el-tag>
            </div>
          </div>
        </section>

        <!-- 去重跳过 -->
        <section v-if="skipped.length" class="res-section warn">
          <header class="sec-head">
            <el-icon :size="14"><WarningFilled /></el-icon>
            <span>已存在，跳过 · {{ skipped.length }} 条</span>
          </header>
          <div class="sec-body flush">
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
        </section>

        <!-- 不支持的选项 -->
        <section v-if="unsupported.length" class="res-section info">
          <header class="sec-head">
            <el-icon :size="14"><InfoFilled /></el-icon>
            <span>部分表不支持，已跳过 · {{ unsupported.length }} 项</span>
          </header>
          <div class="sec-body">
            <div class="line-list">
              <span v-for="(u, i) in unsupported" :key="i" class="line-item">
                {{ u.tableName }}<em class="dim"> @ {{ u.dbCode }}</em>
                不支持{{ u.type === 'permission' ? '权限' : '数据范围' }}「{{ u.label }}」
              </span>
            </div>
          </div>
        </section>

        <!-- 去重检查异常（preview 阶段） -->
        <section v-if="errors.length" class="res-section warn">
          <header class="sec-head">
            <el-icon :size="14"><WarningFilled /></el-icon>
            <span>去重检查异常 · {{ errors.length }} 项（对应数据未做去重）</span>
          </header>
          <div class="sec-body">
            <div class="line-list">
              <span v-for="(e, i) in errors" :key="i" class="line-item">
                {{ e.tableName }}<em class="dim"> @ {{ e.dbCode }}</em>：{{ e.error }}
              </span>
            </div>
          </div>
        </section>
      </div>
    </template>

    <template #footer>
      <div class="dlg-footer">
        <span class="muted footer-hint">
          <template v-if="failItems.length">失败组已回滚，可修正后重试</template>
        </span>
        <el-button type="primary" @click="onOk">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** preview 返回 */
  groups: { type: Array, default: () => [] },
  skipped: { type: Array, default: () => [] },
  unsupported: { type: Array, default: () => [] },
  errors: { type: Array, default: () => [] },
  /** execute 返回：{key, success, rows, error}（key 与 groups[].key 对应） */
  results: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

/** 共提交条数 = 所有 group statements 数 */
const submitted = computed(() =>
  (props.groups || []).reduce((n, g) => n + (g.statements || []).length, 0)
)

/** results 与 groups 元数据按 key 合并；无结果的组按失败展示 */
const merged = computed(() => {
  const gMap = new Map((props.groups || []).map((g) => [g.key, g]))
  const seen = new Set()
  const list = []
  for (const r of props.results || []) {
    seen.add(r.key)
    const g = gMap.get(r.key)
    const [dbCode = '', tableName = ''] = String(r.key || '').split('|')
    list.push({
      key: r.key,
      dbCode: g?.dbCode || dbCode,
      dbName: g?.dbName || '',
      tableName: g?.tableName || tableName,
      stmtCount: (g?.statements || []).length,
      success: !!r.success,
      rows: r.rows,
      error: r.error
    })
  }
  for (const g of props.groups || []) {
    if (!seen.has(g.key)) {
      list.push({
        key: g.key,
        dbCode: g.dbCode,
        dbName: g.dbName,
        tableName: g.tableName,
        stmtCount: (g.statements || []).length,
        success: false,
        rows: 0,
        error: '未返回执行结果'
      })
    }
  }
  return list
})

const okItems = computed(() => merged.value.filter((i) => i.success))
const failItems = computed(() => merged.value.filter((i) => !i.success))
const okRows = computed(() =>
  okItems.value.reduce((n, i) => n + (i.rows ?? i.stmtCount ?? 0), 0)
)

/** 全成功且无任何跳过/不支持/异常 → 简洁成功态 */
const isAllClean = computed(
  () =>
    merged.value.length > 0 &&
    failItems.value.length === 0 &&
    props.skipped.length === 0 &&
    props.unsupported.length === 0 &&
    props.errors.length === 0
)
const isEmpty = computed(
  () =>
    merged.value.length === 0 &&
    props.skipped.length === 0 &&
    props.unsupported.length === 0 &&
    props.errors.length === 0
)

function onOk() {
  emit('update:modelValue', false)
  emit('confirm')
}
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

/* ---- 全成功简洁态 ---- */
.success-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 0 18px;
}
.hero-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  box-shadow: 0 0 0 6px rgba(22, 163, 74, 0.08);
  margin-bottom: 12px;
}
.hero-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ink-900);
}
.hero-sub {
  margin-top: 3px;
  font-variant-numeric: tabular-nums;
}

/* ---- 汇总条 ---- */
.stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.stat-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  color: var(--ink-700);
}
.stat-chip b {
  font-variant-numeric: tabular-nums;
}
.stat-chip.ok {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-7);
  color: var(--el-color-success-dark-2);
}
.stat-chip.bad {
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-7);
  color: var(--el-color-danger-dark-2);
}
.stat-chip.warn {
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-7);
  color: var(--el-color-warning-dark-2);
}
.stat-chip.info {
  background: var(--el-color-info-light-9);
  border-color: var(--el-color-info-light-7);
  color: var(--el-color-info-dark-2);
}

/* ---- 明细分区 ---- */
.sections {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 52vh;
  overflow: auto;
}
.res-section {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface-card);
  flex-shrink: 0;
}
.sec-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.res-section.ok .sec-head {
  color: var(--el-color-success-dark-2);
  background: var(--el-color-success-light-9);
  border-bottom-color: var(--el-color-success-light-8);
}
.res-section.bad .sec-head {
  color: var(--el-color-danger-dark-2);
  background: var(--el-color-danger-light-9);
  border-bottom-color: var(--el-color-danger-light-8);
}
.res-section.warn .sec-head {
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border-bottom-color: var(--el-color-warning-light-8);
}
.res-section.info .sec-head {
  color: var(--el-color-info-dark-2);
  background: var(--el-color-info-light-9);
  border-bottom-color: var(--el-color-info-light-8);
}
.sec-body {
  max-height: 180px;
  overflow: auto;
  padding: 4px 12px 8px;
  font-size: 12px;
}
.sec-body.flush {
  padding: 4px 12px 10px;
}

.res-item {
  padding: 7px 0;
}
.res-item + .res-item {
  border-top: 1px dashed var(--el-border-color-extra-light);
}
.res-item.row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.res-item-main {
  display: flex;
  align-items: center;
  gap: 10px;
}
.res-target {
  font-weight: 500;
  font-size: 13px;
  color: var(--ink-900);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.res-table {
  font-weight: 400;
  font-size: 12px;
  color: var(--ink-500);
}
.res-count {
  margin-left: auto;
  flex-shrink: 0;
  font-weight: 600;
  color: var(--el-color-success-dark-2);
}
.res-tag {
  flex-shrink: 0;
}
.res-item.row .res-tag {
  margin-left: 0;
}
.res-item-main .res-tag {
  margin-left: auto;
}
.res-error {
  margin-top: 5px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger-dark-2);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 明细小表（跳过） */
.detail-table {
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

/* 行式列表（不支持 / 去重异常） */
.line-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.line-item {
  font-weight: 400;
  line-height: 1.6;
}

.dlg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
</style>
