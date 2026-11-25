<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { SemanticCatalog } from "~/api/mcp-tool-test";

defineProps<{ data: SemanticCatalog }>();
const { t } = useI18n();
</script>

<template>
  <el-table :data="data.metrics" border size="small" stripe max-height="430">
    <el-table-column prop="code" :label="t('mcpService.toolTest.metricCode')" min-width="170" />
    <el-table-column prop="name" :label="t('mcpService.toolTest.metricName')" min-width="170" />
    <el-table-column prop="owner" :label="t('mcpService.toolTest.metricOwner')" min-width="110" />
    <el-table-column prop="grain" :label="t('mcpService.toolTest.metricGrain')" width="90" />
    <el-table-column :label="t('mcpService.toolTest.dimensions')" min-width="150">
      <template #default="{ row }">
        {{ (row.allowed_dimensions || []).join(", ") || "—" }}
      </template>
    </el-table-column>
    <el-table-column prop="revision" :label="t('mcpService.toolTest.metricRevisionShort')" width="70" />
  </el-table>
</template>
