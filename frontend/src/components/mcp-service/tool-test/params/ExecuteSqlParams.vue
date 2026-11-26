<script setup lang="ts">
import { reactive } from "vue";
import { useI18n } from "vue-i18n";
import { stripEmpty } from "~/utils/stripEmpty";

defineProps<{ dataSources: { id: string; name: string }[] }>();

const { t } = useI18n();

const form = reactive({ data_source_id: "", sql: "" });
const errors = reactive({ data_source_id: "", sql: "" });

function validate() {
  errors.data_source_id = form.data_source_id
    ? ""
    : t("mcpService.toolTest.requiredHint", { name: t("common.dataSource") });
  errors.sql = form.sql.trim()
    ? ""
    : t("mcpService.toolTest.requiredHint", { name: "SQL" });
  return !errors.data_source_id && !errors.sql;
}

defineExpose({
  validate: () => Promise.resolve(validate()),
  getArgs: () => stripEmpty({ ...form }),
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
        <el-option
          v-for="ds in dataSources"
          :key="ds.id"
          :label="ds.name"
          :value="ds.id"
        />
      </el-select>
    </el-form-item>
    <el-form-item>
      <SqlEditor v-model="form.sql" label="SQL" required @update:model-value="errors.sql = ''" />
      <p v-if="errors.sql" class="tool-test-field-error">{{ errors.sql }}</p>
    </el-form-item>
  </el-form>
</template>
