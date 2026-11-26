<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import { CircleCheck as CircleCheckFilled, CircleX as CircleCloseFilled } from "@lucide/vue";
import type { MetricExecution, SelectResult } from "~/api/mcp-tool-test";

const props = defineProps<{ data: MetricExecution }>();
const { t } = useI18n();
const activeTab = ref("summary");

const selectResults = computed(() =>
  props.data.execution.results.filter(
    (result): result is SelectResult => result.success && result.type === "SELECT",
  ),
);
const firstResult = computed(() => selectResults.value[0]);
const failedResults = computed(() =>
  props.data.execution.results.filter((result) => !result.success),
);
const errorMessages = computed(() =>
  failedResults.value
    .map((result) => (result as { error_message?: string }).error_message)
    .filter((message): message is string => Boolean(message)),
);
const totalRows = computed(() =>
  selectResults.value.reduce((sum, result) => sum + result.returned_rows, 0),
);
const truncated = computed(() => selectResults.value.some((result) => result.truncated));
const statusText = computed(() => {
  if (failedResults.value.length) {
    return t("mcpService.toolTest.metricStatusFailed");
  }
  if (!selectResults.value.length) {
    return t("mcpService.toolTest.metricStatusEmpty");
  }
  return t("mcpService.toolTest.metricStatusSuccess", { count: totalRows.value });
});
</script>

<template>
  <div class="metric-result-card overflow-hidden rounded-xl border border-[var(--ep-border-color-lighter)] bg-[var(--ep-bg-color)]">
    <div class="border-b border-[var(--ep-border-color-lighter)] bg-[var(--ep-fill-color-light)] px-4 py-3">
      <div class="flex flex-wrap items-center gap-2">
        <el-icon v-if="failedResults.length" class="text-[var(--ep-color-danger)]"><CircleCloseFilled /></el-icon>
        <el-icon v-else class="text-[var(--ep-color-success)]"><CircleCheckFilled /></el-icon>
        <span class="text-sm font-semibold text-[var(--ep-text-color-primary)]">{{ data.metric.name }}</span>
        <code class="rounded bg-[var(--ep-fill-color)] px-1.5 py-0.5 text-xs">{{ data.metric.code }}</code>
        <el-tag size="small" effect="plain">{{ t("mcpService.toolTest.metricRevision", { revision: data.metric.revision || 1 }) }}</el-tag>
        <el-tag v-if="truncated" size="small" type="warning" effect="light">{{ t("mcpService.toolTest.truncatedTag") }}</el-tag>
      </div>
      <div class="mt-1 text-xs text-[var(--ep-text-color-secondary)]">
        {{ statusText }}
        <span v-if="data.dimensions.length"> · {{ t("mcpService.toolTest.metricDimensionsLabel") }}: {{ data.dimensions.join(", ") }}</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="metric-result-tabs px-4">
      <el-tab-pane :label="t('mcpService.toolTest.metricSummaryTab')" name="summary">
        <div class="grid gap-3 pb-4 pt-1 md:grid-cols-3">
          <div class="rounded-lg bg-[var(--ep-fill-color-light)] p-3">
            <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("mcpService.toolTest.metricRowsLabel") }}</div>
            <div class="mt-1 text-2xl font-bold text-[var(--ep-text-color-primary)]">{{ totalRows }}</div>
          </div>
          <div class="rounded-lg bg-[var(--ep-fill-color-light)] p-3">
            <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("mcpService.toolTest.metricOwnerLabel") }}</div>
            <div class="mt-1 text-sm font-semibold text-[var(--ep-text-color-primary)]">{{ data.metric.owner || "-" }}</div>
          </div>
          <div class="rounded-lg bg-[var(--ep-fill-color-light)] p-3">
            <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("mcpService.toolTest.metricGrainLabel") }}</div>
            <div class="mt-1 text-sm font-semibold text-[var(--ep-text-color-primary)]">{{ data.metric.grain || "-" }}</div>
          </div>
        </div>
        <div class="pb-4 text-sm leading-6 text-[var(--ep-text-color-regular)]">
          {{ t("mcpService.toolTest.metricSummaryHint") }}
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('mcpService.toolTest.metricDataTab')" name="data">
        <div class="pb-4 pt-1">
          <template v-if="firstResult">
            <el-table :data="firstResult.rows" border size="small" max-height="360" stripe>
              <el-table-column
                v-for="(col, ci) in firstResult.columns"
                :key="ci"
                :prop="String(ci)"
                :label="col"
                show-overflow-tooltip
              />
            </el-table>
            <div class="mt-2 text-xs text-[var(--ep-text-color-secondary)]">
              {{ t("mcpService.toolTest.returnedRows", { total: firstResult.returned_rows }) }}
            </div>
          </template>
          <div v-if="errorMessages.length" class="mt-3 rounded-lg border border-[var(--ep-color-danger-light-7)] bg-[var(--ep-color-danger-light-9)] p-3 text-xs leading-5 text-[var(--ep-color-danger)]">
            <div v-for="message in errorMessages" :key="message" class="font-mono">{{ message }}</div>
          </div>
          <el-empty v-if="!firstResult" :description="t('mcpService.toolTest.emptyResult')" :image-size="72" />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('mcpService.toolTest.metricSqlTab')" name="sql">
        <div class="pb-4 pt-1">
          <pre class="sql-evidence">{{ data.execution.executed_sql }}</pre>
          <div v-if="data.execution.bindings?.length" class="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-xs">
            <span v-for="(val, i) in data.execution.bindings" :key="i" class="text-[var(--ep-text-color-secondary)]">
              ?{{ i + 1 }} = <code class="text-[var(--ep-color-primary)]">{{ val }}</code>
            </span>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('mcpService.toolTest.metricEvidenceTab')" name="evidence">
        <el-descriptions :column="2" border size="small" class="pb-4 pt-1">
          <el-descriptions-item label="metric_id">{{ data.metric.metric_id }}</el-descriptions-item>
          <el-descriptions-item label="subject_id">{{ data.metric.subject_id }}</el-descriptions-item>
          <el-descriptions-item :label="t('mcpService.toolTest.metricTimeFieldLabel')">{{ data.metric.time_field || "-" }}</el-descriptions-item>
          <el-descriptions-item :label="t('mcpService.toolTest.metricTimeZoneLabel')">{{ data.metric.time_zone || "-" }}</el-descriptions-item>
          <el-descriptions-item :label="t('mcpService.toolTest.metricArgumentsLabel')" :span="2">
            <code>{{ JSON.stringify(data.parameters) }}</code>
          </el-descriptions-item>
          <el-descriptions-item :label="t('mcpService.toolTest.metricDimensionsLabel')" :span="2">
            {{ data.dimensions.join(", ") || "-" }}
          </el-descriptions-item>
          <el-descriptions-item v-if="errorMessages.length" label="error" :span="2">
            <div v-for="message in errorMessages" :key="message" class="font-mono text-[var(--ep-color-danger)]">{{ message }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.metric-result-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}
.sql-evidence {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  border-radius: 8px;
  background: var(--ep-fill-color-light);
  padding: 12px 14px;
  color: var(--ep-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
