<script setup lang="ts">
import { reactive } from "vue";
import { useI18n } from "vue-i18n";

const { t } = useI18n();

const form = reactive({
  metric: "",
});
const errors = reactive({ metric: "" });

function validate() {
  errors.metric = form.metric.trim()
    ? ""
    : t("mcpService.toolTest.requiredHint", { name: t("mcpService.toolTest.metric") });
  return !errors.metric;
}

defineExpose({
  validate: () => Promise.resolve(validate()),
  getArgs: () => ({
    metric: form.metric.trim(),
  }),
});
</script>

<template>
  <el-form label-position="top" size="small">
    <el-form-item :label="t('mcpService.toolTest.metric')" required :error="errors.metric">
      <el-input
        v-model="form.metric"
        :placeholder="t('mcpService.toolTest.metricPlaceholder')"
        @input="errors.metric = ''"
      />
    </el-form-item>
  </el-form>
</template>
