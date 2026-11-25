<template>
  <el-select
    :model-value="modelValue"
    multiple
    @update:model-value="updateModelValue"
  >
    <template #header>
      <div class="select-all-tools">
        <span class="selection-count" aria-live="polite">
          已选 {{ selectedCount }} · 可选 {{ selectableCount }}
        </span>
        <div class="selection-actions">
          <el-button
            type="primary"
            text
            size="small"
            :disabled="!selectableCount || allSelected"
            title="选中当前全部未禁用选项"
            @click="selectAll"
          >
            全选
          </el-button>
          <el-button
            text
            size="small"
            :disabled="!selectedCount"
            @click="clearSelection"
          >
            清空
          </el-button>
        </div>
      </div>
    </template>
    <slot />
  </el-select>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  options: {
    type: Array,
    default: () => []
  },
  optionValueKey: {
    type: String,
    required: true
  },
  disabledKey: {
    type: String,
    default: 'disabled'
  }
})

const emit = defineEmits(['update:modelValue'])

const selectedValues = computed(() => (Array.isArray(props.modelValue) ? props.modelValue : []))
const selectedSet = computed(() => new Set(selectedValues.value))
const selectedCount = computed(() => selectedSet.value.size)

const selectableValues = computed(() => {
  const values = []
  const seen = new Set()
  for (const option of props.options || []) {
    if (!option || option[props.disabledKey]) continue
    const value = option[props.optionValueKey]
    if (seen.has(value)) continue
    seen.add(value)
    values.push(value)
  }
  return values
})
const selectableCount = computed(() => selectableValues.value.length)
const allSelected = computed(
  () =>
    selectableValues.value.length > 0 &&
    selectableValues.value.every((value) => selectedSet.value.has(value))
)

function updateModelValue(value) {
  emit('update:modelValue', Array.isArray(value) ? value : [])
}

function selectAll() {
  emit('update:modelValue', [...selectableValues.value])
}

function clearSelection() {
  emit('update:modelValue', [])
}
</script>

<style scoped>
.select-all-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}
.selection-count {
  flex-shrink: 0;
  color: var(--ink-500);
  font-size: 12px;
  line-height: 24px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.selection-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.selection-actions .el-button + .el-button {
  margin-left: 0;
}
</style>
