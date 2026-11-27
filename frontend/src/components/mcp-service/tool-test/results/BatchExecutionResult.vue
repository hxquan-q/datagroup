<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { BatchExecution, SqlExecution } from "~/api/mcp-tool-test";

defineProps<{ data: BatchExecution }>();
const { t } = useI18n();
</script>

<template>
  <div class="batch-result flex flex-col gap-4">
    <div v-for="(step, i) in data.steps" :key="i" class="batch-step">
      <div class="step-header">
        <el-tag :type="step.success ? 'success' : 'danger'" size="small" effect="plain">
          {{ t("mcpService.toolTest.batchStep", { index: i + 1 }) }}
        </el-tag>
        <span class="step-sql font-mono">{{ step.sql.slice(0, 120) }}</span>
      </div>
      <pre v-if="step.error" class="step-error">{{ step.error }}</pre>
      <SqlExecutionResult v-else-if="step.execution" :data="step.execution as SqlExecution" />
    </div>
  </div>
</template>

<style scoped>
.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.step-sql {
  font-size: 12px;
  color: var(--ep-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.step-error {
  font-family: monospace;
  font-size: 12px;
  color: var(--ep-color-danger);
  background: var(--ep-color-danger-light-9);
  border-radius: 4px;
  padding: 8px 10px;
  white-space: pre-wrap;
}
</style>
