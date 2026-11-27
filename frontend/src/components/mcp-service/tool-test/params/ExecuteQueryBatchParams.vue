<script setup lang="ts">
import { reactive } from "vue";
import { useI18n } from "vue-i18n";
import { stripEmpty } from "~/utils/stripEmpty";

defineProps<{ dataSources: { id: string; name: string }[] }>();

const { t } = useI18n();

const form = reactive({ data_source_id: "", statementsText: "" });
const errors = reactive({ data_source_id: "", statementsText: "" });

function parseStatements() {
  return form.statementsText
    .split(/\n\s*\n/)
    .map((block) => block.trim())
    .filter(Boolean);
}

function validate() {
  errors.data_source_id = form.data_source_id
    ? ""
    : t("mcpService.toolTest.requiredHint", { name: t("common.dataSource") });
  const statements = parseStatements();
  errors.statementsText = statements.length
    ? ""
    : t("mcpService.toolTest.requiredHint", { name: t("mcpService.toolTest.batchStatements") });
  return !errors.data_source_id && !errors.statementsText;
}

defineExpose({
  validate: () => Promise.resolve(validate()),
  getArgs: () => stripEmpty({
    data_source_id: form.data_source_id,
    statements: parseStatements(),
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
    <el-form-item :label="t('mcpService.toolTest.batchStatements')" required :error="errors.statementsText">
      <el-input
        v-model="form.statementsText"
        type="textarea"
        :rows="10"
        spellcheck="false"
        class="font-mono"
        :placeholder="t('mcpService.toolTest.batchStatementsPlaceholder')"
        @input="errors.statementsText = ''"
      />
    </el-form-item>
  </el-form>
</template>
