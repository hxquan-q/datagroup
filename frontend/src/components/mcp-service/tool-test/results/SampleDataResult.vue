<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { SampleData } from "~/api/mcp-tool-test";

defineProps<{ data: SampleData }>();
const { t } = useI18n();
</script>

<template>
  <div>
    <div v-if="data.masked_columns?.length" class="masked-hint">
      {{ t("mcpService.toolTest.maskedHint", { columns: data.masked_columns.join(", ") }) }}
    </div>
    <el-table :data="data.rows.map((r) => Object.fromEntries(r.map((v, i) => [String(i), v])))" border size="small" max-height="430">
      <el-table-column
        v-for="(col, i) in data.columns"
        :key="col"
        :prop="String(i)"
        :label="col"
        min-width="120"
      />
    </el-table>
  </div>
</template>

<style scoped>
.masked-hint {
  font-size: 12px;
  color: var(--ep-color-warning);
  margin-bottom: 8px;
}
</style>
