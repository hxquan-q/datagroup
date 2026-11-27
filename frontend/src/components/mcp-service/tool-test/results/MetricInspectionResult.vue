<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { MetricInspection } from "~/api/mcp-tool-test";

defineProps<{ data: MetricInspection }>();
const { t } = useI18n();
</script>

<template>
  <div class="inspection-result flex flex-col gap-4">
    <div class="detail-section">
      <div class="section-caption">{{ t("mcpService.toolTest.metricDefinition") }}</div>
      <dl class="inspection-list">
        <div><dt>{{ t("mcpService.toolTest.metricId") }}</dt><dd class="font-mono break-all">{{ data.metric.metric_id }}</dd></div>
        <div><dt>{{ t("mcpService.toolTest.metricCode") }}</dt><dd>{{ data.metric.code }}</dd></div>
        <div><dt>{{ t("mcpService.toolTest.metricName") }}</dt><dd>{{ data.metric.name }}</dd></div>
        <div><dt>{{ t("mcpService.toolTest.measureExpression") }}</dt><dd class="font-mono">{{ data.metric.measure_expression || "—" }}</dd></div>
        <div><dt>{{ t("mcpService.toolTest.factTable") }}</dt><dd class="font-mono">{{ data.metric.fact_table_name || "—" }}</dd></div>
        <div><dt>{{ t("mcpService.toolTest.metricTimeField") }}</dt><dd class="font-mono">{{ data.metric.time_field || "—" }}</dd></div>
      </dl>
    </div>

    <div v-if="data.dimensions.length" class="detail-section">
      <div class="section-caption">{{ t("mcpService.toolTest.dimensions") }}</div>
      <el-table :data="data.dimensions" border size="small">
        <el-table-column prop="name" :label="t('semantic.name')" min-width="110" />
        <el-table-column prop="table_name" :label="t('mcpService.toolTest.dimensionTable')" min-width="130" />
        <el-table-column prop="column" :label="t('mcpService.toolTest.dimensionColumn')" min-width="110" />
      </el-table>
    </div>

    <div v-if="data.join_paths.length" class="detail-section">
      <div class="section-caption">{{ t("mcpService.toolTest.joinPaths") }}</div>
      <el-table :data="data.join_paths" border size="small">
        <el-table-column min-width="240">
          <template #header>{{ t("mcpService.toolTest.joinPath") }}</template>
          <template #default="{ row }">
            <span class="font-mono text-xs">{{ row.fact_table }}.{{ row.fact_column }} → {{ row.dimension_table }}.{{ row.dimension_column }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="cardinality" label="Cardinality" width="110" />
      </el-table>
    </div>

    <div v-if="data.parameters.length" class="detail-section">
      <div class="section-caption">{{ t("mcpService.toolTest.parameters") }}</div>
      <el-table :data="data.parameters" border size="small">
        <el-table-column prop="name" :label="t('semantic.name')" min-width="110" />
        <el-table-column prop="type" label="Type" width="90" />
        <el-table-column :label="t('mcpService.toolTest.required')" width="90">
          <template #default="{ row }">{{ row.required ? "✓" : "—" }}</template>
        </el-table-column>
      </el-table>
    </div>

    <div class="detail-section">
      <div class="section-caption">{{ t("mcpService.toolTest.exampleInvocation") }}</div>
      <pre class="example-pre">{{ JSON.stringify(data.example.arguments, null, 2) }}</pre>
    </div>
  </div>
</template>

<style scoped>
.section-caption {
  font-size: 12px;
  font-weight: 600;
  color: var(--ep-text-color-secondary);
  margin-bottom: 8px;
}
.inspection-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  margin: 0;
}
.inspection-list dt {
  font-size: 12px;
  color: var(--ep-text-color-secondary);
}
.inspection-list dd {
  font-size: 13px;
  margin: 0;
  color: var(--ep-text-color-primary);
}
.example-pre {
  font-family: monospace;
  font-size: 12px;
  background: var(--ep-fill-color-light);
  border-radius: 6px;
  padding: 10px 12px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
