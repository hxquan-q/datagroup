<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import type { MetricParameter, MetricPayload, MetricVO } from "~/api/metric";
import { createMetric, updateMetric } from "~/api/metric";
import { notifyError } from "~/api/http";

const props = defineProps<{
  visible: boolean;
  subjectId: string;
  metric?: MetricVO | null;
}>();
const emit = defineEmits<{
  (e: "update:visible", value: boolean): void;
  (e: "saved"): void;
}>();

const { t } = useI18n();
const activeStep = ref(0);
const saving = ref(false);
const parametersJson = ref("[]");
const form = ref<MetricPayload>(emptyForm());

function emptyForm(): MetricPayload {
  return {
    code: "",
    name: "",
    description: "",
    aliases: [],
    owner: "",
    grain: "",
    time_field: "",
    time_zone: "Asia/Shanghai",
    allowed_dimensions: [],
    sql_template: "",
    parameters: [],
    timeout: 30,
    max_rows: 1000,
  };
}

const isEdit = computed(() => Boolean(props.metric?.id));
const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit("update:visible", value),
});

watch(
  () => props.visible,
  (visible) => {
    if (!visible) return;
    activeStep.value = 0;
    if (props.metric) {
      const metric = props.metric;
      form.value = {
        code: metric.code,
        name: metric.name,
        description: metric.description || "",
        aliases: [...(metric.aliases || [])],
        owner: metric.owner || "",
        grain: metric.grain || "",
        time_field: metric.time_field || "",
        time_zone: metric.time_zone || "",
        allowed_dimensions: [...(metric.allowed_dimensions || [])],
        sql_template: metric.sql_template || "",
        parameters: metric.parameters || [],
        timeout: metric.timeout || 30,
        max_rows: metric.max_rows || 1000,
      };
      parametersJson.value = JSON.stringify(metric.parameters || [], null, 2);
    } else {
      form.value = emptyForm();
      parametersJson.value = "[]";
    }
  },
);

function validateBasic(): boolean {
  if (!form.value.code?.trim() || !form.value.name?.trim()) {
    ElMessage.warning(t("metric.basicRequired"));
    return false;
  }
  return true;
}

function parseParameters(): MetricParameter[] | null {
  try {
    const parsed = JSON.parse(parametersJson.value || "[]");
    if (!Array.isArray(parsed)) throw new Error("not-array");
    const names = new Set<string>();
    for (const item of parsed) {
      if (!item || typeof item !== "object" || !item.name?.trim() || !item.type?.trim()) {
        ElMessage.error(t("metric.parameterTypeRequired"));
        return null;
      }
      if (names.has(item.name)) {
        ElMessage.error(t("metric.parameterDuplicate", { name: item.name }));
        return null;
      }
      names.add(item.name);
    }
    return parsed;
  } catch {
    ElMessage.error(t("metric.parametersInvalid"));
    return null;
  }
}

function validateDefinition(): boolean {
  if (!form.value.sql_template?.trim()) {
    ElMessage.warning(t("metric.sqlRequired"));
    return false;
  }
  const parameters = parseParameters();
  if (!parameters) return false;
  form.value.parameters = parameters;
  return true;
}

function next() {
  if (activeStep.value === 0 && !validateBasic()) return;
  if (activeStep.value === 1 && !validateDefinition()) return;
  activeStep.value += 1;
}

async function save() {
  if (!validateBasic() || !validateDefinition()) return;
  saving.value = true;
  try {
    const payload: MetricPayload = {
      ...form.value,
      code: form.value.code?.trim(),
      name: form.value.name.trim(),
      description: form.value.description || "",
      owner: form.value.owner || "",
      grain: form.value.grain || "",
      time_field: form.value.time_field || "",
      time_zone: form.value.time_zone || "",
      sql_template: form.value.sql_template,
    };
    if (isEdit.value && props.metric?.id) {
      const updatePayload = { ...payload };
      delete updatePayload.code;
      await updateMetric(props.metric.id, updatePayload);
    } else {
      await createMetric(props.subjectId, payload);
    }
    ElMessage.success(t(isEdit.value ? "metric.updateSuccess" : "metric.createSuccess"));
    dialogVisible.value = false;
    emit("saved");
  } catch (error) {
    notifyError(error, t("common.operationFailed"));
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? t('metric.editTitle') : t('metric.createTitle')"
    width="920px"
    destroy-on-close
    class="metric-wizard-dialog"
  >
    <el-steps :active="activeStep" align-center finish-status="success" class="mb-6">
      <el-step :title="t('metric.stepBasic')" />
      <el-step :title="t('metric.stepDefinition')" />
      <el-step :title="t('metric.stepReview')" />
    </el-steps>

    <div v-show="activeStep === 0" class="grid gap-4 md:grid-cols-2">
      <el-form label-position="top" class="contents">
        <el-form-item :label="t('metric.code')" required>
          <el-input v-model="form.code" :disabled="isEdit" placeholder="quality_daily_fpy" />
        </el-form-item>
        <el-form-item :label="t('common.name')" required>
          <el-input v-model="form.name" :placeholder="t('metric.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('common.aliases')" class="md:col-span-2">
          <el-input-tag v-model="form.aliases" :placeholder="t('metric.aliasesPlaceholder')" class="w-full" />
        </el-form-item>
        <el-form-item :label="t('metric.owner')">
          <el-input v-model="form.owner" :placeholder="t('metric.ownerPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('metric.grain')">
          <el-input v-model="form.grain" placeholder="day / work_order / line" />
        </el-form-item>
        <el-form-item :label="t('common.description')" class="md:col-span-2">
          <el-input v-model="form.description" type="textarea" :rows="3" :placeholder="t('metric.descriptionPlaceholder')" />
        </el-form-item>
      </el-form>
    </div>

    <div v-show="activeStep === 1" class="flex flex-col gap-4">
      <div class="grid gap-4 md:grid-cols-3">
        <el-form label-position="top" class="contents">
          <el-form-item :label="t('metric.timeField')">
            <el-input v-model="form.time_field" placeholder="stat_date" />
          </el-form-item>
          <el-form-item :label="t('metric.timeZone')">
            <el-input v-model="form.time_zone" placeholder="Asia/Shanghai" />
          </el-form-item>
          <el-form-item :label="t('metric.allowedDimensions')">
            <el-input-tag v-model="form.allowed_dimensions" :placeholder="t('metric.dimensionsPlaceholder')" class="w-full" />
          </el-form-item>
        </el-form>
      </div>
      <SqlTemplateEditor v-model="form.sql_template" :label="t('metric.sqlTemplate')" required />
      <el-form label-position="top">
        <el-form-item :label="t('metric.parametersJson')">
          <el-input v-model="parametersJson" type="textarea" :rows="7" spellcheck="false" />
          <div class="mt-1 text-xs text-[var(--ep-text-color-secondary)]">{{ t("metric.parametersHint") }}</div>
        </el-form-item>
      </el-form>
      <div class="grid gap-4 md:grid-cols-2">
        <el-form label-position="top" class="contents">
          <el-form-item :label="t('metric.timeout')">
            <el-input-number v-model="form.timeout" :min="1" :max="300" class="w-full" />
          </el-form-item>
          <el-form-item :label="t('metric.maxRows')">
            <el-input-number v-model="form.max_rows" :min="1" :max="10000" class="w-full" />
          </el-form-item>
        </el-form>
      </div>
    </div>

    <div v-show="activeStep === 2" class="rounded-xl border border-[var(--ep-border-color-lighter)] bg-[var(--ep-fill-color-light)] p-4">
      <div class="mb-4 text-sm font-semibold text-[var(--ep-text-color-primary)]">{{ t("metric.reviewTitle") }}</div>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item :label="t('metric.code')">{{ form.code }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.name')">{{ form.name }}</el-descriptions-item>
        <el-descriptions-item :label="t('metric.owner')">{{ form.owner || "-" }}</el-descriptions-item>
        <el-descriptions-item :label="t('metric.grain')">{{ form.grain || "-" }}</el-descriptions-item>
        <el-descriptions-item :label="t('metric.allowedDimensions')" :span="2">{{ form.allowed_dimensions?.join(", ") || "-" }}</el-descriptions-item>
        <el-descriptions-item :label="t('metric.parameters')" :span="2">{{ form.parameters?.length || 0 }}</el-descriptions-item>
      </el-descriptions>
      <pre class="mt-4 max-h-52 overflow-auto rounded-lg bg-[var(--ep-bg-color)] p-3 text-xs leading-6">{{ form.sql_template }}</pre>
      <div class="mt-3 text-xs leading-5 text-[var(--ep-text-color-secondary)]">{{ t("metric.reviewHint") }}</div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between">
        <el-button :disabled="activeStep === 0" @click="activeStep -= 1">{{ t("metric.previous") }}</el-button>
        <div class="flex gap-2">
          <el-button @click="dialogVisible = false">{{ t("common.cancel") }}</el-button>
          <el-button v-if="activeStep < 2" type="primary" @click="next">{{ t("metric.next") }}</el-button>
          <el-button v-else type="primary" :loading="saving" @click="save">{{ t("metric.saveMetric") }}</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>
