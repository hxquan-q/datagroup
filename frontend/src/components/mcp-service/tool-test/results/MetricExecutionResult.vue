<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { MetricExecution } from "~/api/mcp-tool-test";

defineProps<{ data: MetricExecution }>();
const { t } = useI18n();
</script>

<template>
  <div
    class="metric-summary mb-3 rounded-lg border border-[var(--ep-border-color-lighter)] bg-[var(--ep-fill-color-light)] p-3 text-[13px]"
  >
    <div class="font-semibold text-[var(--ep-text-color-primary)]">
      {{ data.metric.name }}
      <code class="ml-2 text-xs">{{ data.metric.code }}</code>
    </div>
    <div class="mt-1 text-xs text-[var(--ep-text-color-secondary)]">
      {{ t("mcpService.toolTest.metricRevision", { revision: data.metric.revision || 1 }) }}
      <span v-if="data.dimensions.length"> · {{ t("mcpService.toolTest.metricDimensionsLabel") }}: {{ data.dimensions.join(", ") }}</span>
    </div>
  </div>
  <SqlExecutionResult :data="data.execution" />
</template>
