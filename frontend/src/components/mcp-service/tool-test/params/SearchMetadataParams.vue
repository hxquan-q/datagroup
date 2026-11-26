<script setup lang="ts">
import { reactive } from "vue";
import { useI18n } from "vue-i18n";
import { stripEmpty } from "~/utils/stripEmpty";

const props = defineProps<{ requireKeywords?: boolean }>();

const { t } = useI18n();

const form = reactive({ keywords: [] as string[] });
const errors = reactive({ keywords: "" });

function validate() {
  errors.keywords = !props.requireKeywords || form.keywords.some((keyword) => keyword.trim())
    ? ""
    : t("mcpService.toolTest.requiredHint", { name: t("mcpService.toolTest.keywords") });
  return !errors.keywords;
}

defineExpose({
  validate: () => Promise.resolve(validate()),
  getArgs: () => stripEmpty({ ...form }),
});
</script>

<template>
  <el-form label-position="top" size="small">
    <el-form-item :label="t('mcpService.toolTest.keywords')" :required="requireKeywords" :error="errors.keywords">
      <el-input-tag
        v-model="form.keywords"
        size="small"
        class="w-full"
        :placeholder="t('mcpService.toolTest.keywordsPlaceholder')"
        @change="errors.keywords = ''"
      />
    </el-form-item>
  </el-form>
</template>
