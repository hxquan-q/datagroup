<script setup lang="ts">
import { reactive } from "vue";
import { useI18n } from "vue-i18n";

const { t } = useI18n();

const form = reactive({
  metric: "",
  argumentsJson: "{}",
  dimensions: [] as string[],
});
const errors = reactive({ metric: "", argumentsJson: "" });

function assertSafeIntegerLiterals(source: string) {
  let inString = false;
  let escaped = false;
  let quote = "";
  for (let i = 0; i < source.length; i++) {
    const ch = source[i];
    if (inString) {
      if (escaped) escaped = false;
      else if (ch === "\\") escaped = true;
      else if (ch === quote) inString = false;
      continue;
    }
    if (ch === '"' || ch === "'") {
      inString = true;
      quote = ch;
      continue;
    }
    if (!/[0-9]/.test(ch)) continue;
    const prev = source[i - 1] || "";
    if (/[A-Za-z0-9_.$]/.test(prev)) continue;
    let end = i + 1;
    while (end < source.length && /[0-9]/.test(source[end])) end++;
    if (end - i > 15 && source[end] !== ".") {
      throw new Error(t("mcpService.toolTest.metricArgumentsUnsafeInteger"));
    }
    i = end - 1;
  }
}

function parseMetricArguments() {
  let metricArguments: Record<string, any> = {};
  const raw = form.argumentsJson.trim();
  if (raw) {
    assertSafeIntegerLiterals(raw);
    const parsed = JSON.parse(raw);
    if (parsed == null || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error(t("mcpService.toolTest.metricArgumentsInvalid"));
    }
    metricArguments = parsed;
  }
  return metricArguments;
}

function validate() {
  errors.metric = form.metric.trim()
    ? ""
    : t("mcpService.toolTest.requiredHint", { name: t("mcpService.toolTest.metric") });
  errors.argumentsJson = "";
  try {
    parseMetricArguments();
  } catch (error) {
    errors.argumentsJson = error instanceof Error ? error.message : t("mcpService.toolTest.metricArgumentsInvalid");
  }
  return !errors.metric && !errors.argumentsJson;
}

defineExpose({
  validate: () => Promise.resolve(validate()),
  getArgs: () => {
    const metricArguments = parseMetricArguments();
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
    <el-form-item :label="t('mcpService.toolTest.metric')" required :error="errors.metric">
      <el-input
        v-model="form.metric"
        :placeholder="t('mcpService.toolTest.metricPlaceholder')"
        @input="errors.metric = ''"
      />
    </el-form-item>
    <el-form-item :label="t('mcpService.toolTest.metricArguments')" :error="errors.argumentsJson">
      <el-input
        v-model="form.argumentsJson"
        type="textarea"
        :rows="8"
        spellcheck="false"
        class="font-mono"
        @input="errors.argumentsJson = ''"
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
