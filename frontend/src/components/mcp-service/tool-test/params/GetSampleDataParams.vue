<script setup lang="ts">
import { reactive } from "vue";
import { useI18n } from "vue-i18n";
import { stripEmpty } from "~/utils/stripEmpty";

defineProps<{ dataSources: { id: string; name: string }[] }>();

const { t } = useI18n();

const form = reactive({ data_source_id: "", table_name: "", columns: [] as string[], limit: 10 });
const errors = reactive({ data_source_id: "", table_name: "" });

function validate() {
  errors.data_source_id = form.data_source_id
    ? ""
    : t("mcpService.toolTest.requiredHint", { name: t("common.dataSource") });
  errors.table_name = form.table_name.trim()
    ? ""
    : t("mcpService.toolTest.requiredHint", { name: t("mcpService.toolTest.tableName") });
  return !errors.data_source_id && !errors.table_name;
}

defineExpose({
  validate: () => Promise.resolve(validate()),
  getArgs: () => stripEmpty({
    data_source_id: form.data_source_id,
    table_name: form.table_name.trim(),
    columns: form.columns.length ? form.columns : undefined,
    limit: form.limit,
  }),
});
</script>

<template>
  <el-form label-position="top" size="small">
    <el-form-item :label="t('common.dataSource')" required :error="errors.data_source_id">
      <el-select
        v-model="form.data_source_id"
        size="small"
        class="w-full"
        :placeholder="t('mcpService.toolTest.dataSourcePlaceholder')"
        filterable
        @change="errors.data_source_id = ''"
      >
        <el-option v-for="ds in dataSources" :key="ds.id" :label="ds.name" :value="ds.id" />
      </el-select>
    </el-form-item>
    <el-form-item :label="t('mcpService.toolTest.tableName')" required :error="errors.table_name">
      <el-input v-model="form.table_name" placeholder="schema.table" @input="errors.table_name = ''" />
    </el-form-item>
    <el-form-item :label="t('mcpService.toolTest.sampleColumns')">
      <el-input-tag v-model="form.columns" size="small" class="w-full" :placeholder="t('mcpService.toolTest.sampleColumnsPlaceholder')" />
    </el-form-item>
    <el-form-item :label="t('mcpService.toolTest.sampleLimit')">
      <el-input-number v-model="form.limit" :min="1" :max="20" size="small" class="w-full" />
    </el-form-item>
  </el-form>
</template>
