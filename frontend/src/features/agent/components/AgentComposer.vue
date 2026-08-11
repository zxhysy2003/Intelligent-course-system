<template>
  <div class="composer">
    <el-input
      :model-value="modelValue"
      type="textarea"
      :autosize="{ minRows: 2, maxRows: 5 }"
      maxlength="2000"
      show-word-limit
      placeholder="问问下一步学什么、为什么推荐这门课、哪些知识点需要先补齐"
      @update:model-value="emit('update:modelValue', $event)"
      @keydown.ctrl.enter.prevent="emit('send')"
      @keydown.meta.enter.prevent="emit('send')"
    />
    <el-button
      type="primary"
      :icon="Promotion"
      :loading="sending"
      :disabled="sending"
      @click="emit('send')"
    >
      发送
    </el-button>
  </div>
</template>

<script setup>
import { Promotion } from '@element-plus/icons-vue'

defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  sending: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:modelValue', 'send'])
</script>

<style scoped>
.composer {
  border-top: 1px solid #e5e7eb;
  padding: 14px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: end;
  background: #ffffff;
}
</style>
