<script setup lang="ts">
import { reactive } from "vue";
import { useI18n } from "vue-i18n";

const { t } = useI18n();

const form = reactive({
  metric: "",
  argumentsJson: "{}",
  dimensions: [] as string[],
});

defineExpose({
  getArgs: () => {
    let metricArguments: Record<string, any> = {};
    const raw = form.argumentsJson.trim();
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed == null || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error(t("mcpService.toolTest.metricArgumentsInvalid"));
      }
      metricArguments = parsed;
    }
    return {
      metric: form.metric.trim(),
      arguments: metricArguments,
      dimensions: form.dimensions,
    };
  },
});
</script>

<template>
  <el-form label-position="top" size="small">
    <el-form-item :label="t('mcpService.toolTest.metric')" required>
      <el-input
        v-model="form.metric"
        :placeholder="t('mcpService.toolTest.metricPlaceholder')"
      />
    </el-form-item>
    <el-form-item :label="t('mcpService.toolTest.metricArguments')">
      <el-input
        v-model="form.argumentsJson"
        type="textarea"
        :rows="8"
        spellcheck="false"
        class="font-mono"
      />
    </el-form-item>
    <el-form-item :label="t('mcpService.toolTest.dimensions')">
      <el-input-tag
        v-model="form.dimensions"
        size="small"
        class="w-full"
        :placeholder="t('mcpService.toolTest.dimensionsPlaceholder')"
      />
    </el-form-item>
  </el-form>
</template>
