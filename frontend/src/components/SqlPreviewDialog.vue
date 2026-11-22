<template>
  <el-dialog
    :model-value="modelValue"
    title="SQL 预览"
    width="760px"
    :close-on-click-modal="false"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-empty v-if="!groups || groups.length === 0" description="没有需要执行的 SQL" />
    <el-tabs v-else type="border-card" class="preview-tabs">
      <el-tab-pane v-for="g in groups" :key="g.key" :label="`${g.dbName} (${g.tableName})`">
        <div class="jdbc muted">{{ g.jdbcUrl }}</div>
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
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="executing"
        :disabled="!groups || groups.length === 0"
        @click="emit('confirm', groups)"
      >
        确定执行
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
const MAX_SHOW = 50

defineProps({
  modelValue: { type: Boolean, default: false },
  groups: { type: Array, default: () => [] },
  executing: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'confirm'])
</script>

<style scoped>
.preview-tabs {
  border: 1px solid #e4e7ed;
}
.jdbc {
  margin-bottom: 8px;
  word-break: break-all;
}
.sql-list {
  max-height: 380px;
  overflow: auto;
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 4px 10px;
}
.sql-line {
  margin: 6px 0;
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  color: #303133;
}
.more-tip {
  margin-top: 8px;
}
.count-tip {
  margin-top: 8px;
  text-align: right;
}
</style>
