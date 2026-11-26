<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Refresh } from "@element-plus/icons-vue";
import type { MetricVO } from "~/api/metric";
import { listMetrics } from "~/api/metric";
import type { ValidationCasePayload, ValidationCaseVO } from "~/api/validation-case";
import {
  createValidationCase,
  deleteValidationCase,
  listValidationCases,
  runValidationCase,
  updateValidationCase,
} from "~/api/validation-case";
import { notifyError } from "~/api/http";
import { formatDateTime } from "~/composables";

const props = defineProps<{ subjectId: string }>();
const { t } = useI18n();

const loading = ref(false);
const runningId = ref("");
const cases = ref<ValidationCaseVO[]>([]);
const metrics = ref<MetricVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const dialogVisible = ref(false);
const saving = ref(false);
const editingCase = ref<ValidationCaseVO | null>(null);
const detailCase = ref<ValidationCaseVO | null>(null);
const detailVisible = ref(false);

const emptyForm = (): ValidationCasePayload & { argumentsJson: string; dimensionsText: string } => ({
  name: "",
  description: "",
  metric_id: "",
  question: "",
  argumentsJson: "{}",
  dimensionsText: "",
  assertion_type: "EXECUTION_SUCCESS",
  expected_value: "",
  owner: "",
  enabled: true,
});

const form = ref(emptyForm());
const enabledCases = computed(() => cases.value.filter((item) => item.enabled !== false));
const passCount = computed(() => enabledCases.value.filter((item) => item.last_status === "PASS").length);
const failCount = computed(() => enabledCases.value.filter((item) => item.last_status === "FAIL").length);
const errorCount = computed(() => enabledCases.value.filter((item) => item.last_status === "ERROR").length);
const healthScore = computed(() => {
  const completed = passCount.value + failCount.value + errorCount.value;
  return completed ? Math.round((passCount.value / completed) * 100) : null;
});
const metricOptions = computed(() => metrics.value.map((metric) => ({ label: `${metric.name} (${metric.code})`, value: metric.id })));

async function loadCases() {
  loading.value = true;
  try {
    const resp = await listValidationCases(props.subjectId, page.value, size.value);
    cases.value = resp.data;
    total.value = resp.total;
  } catch (error) {
    notifyError(error, t("common.loadFailed"));
  } finally {
    loading.value = false;
  }
}

async function loadMetrics() {
  try {
    const resp = await listMetrics(props.subjectId, 1, 100);
    metrics.value = resp.data;
  } catch (error) {
    notifyError(error, t("common.loadFailed"));
  }
}

function openCreate() {
  editingCase.value = null;
  form.value = emptyForm();
  dialogVisible.value = true;
}

function openEdit(item: ValidationCaseVO) {
  editingCase.value = item;
  form.value = {
    ...emptyForm(),
    name: item.name || "",
    description: item.description || "",
    metric_id: item.metric_id,
    question: item.question || "",
    argumentsJson: JSON.stringify(item.arguments || {}, null, 2),
    dimensionsText: (item.dimensions || []).join(", "),
    assertion_type: item.assertion_type || "EXECUTION_SUCCESS",
    expected_value: item.expected_value || "",
    owner: item.owner || "",
    enabled: item.enabled !== false,
  };
  dialogVisible.value = true;
}

function parseJsonObject(raw: string): Record<string, unknown> | null {
  try {
    const parsed = JSON.parse(raw || "{}");
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("invalid");
    return parsed;
  } catch {
    ElMessage.error(t("validation.argumentsInvalid"));
    return null;
  }
}

async function saveCase() {
  const args = parseJsonObject(form.value.argumentsJson || "{}");
  if (!args) return;
  if (!form.value.name.trim() || !form.value.metric_id || !(form.value.question || "").trim()) {
    ElMessage.warning(t("validation.requiredFields"));
    return;
  }
  saving.value = true;
  try {
    const payload: ValidationCasePayload = {
      name: form.value.name,
      description: form.value.description || "",
      metric_id: form.value.metric_id,
      question: form.value.question,
      arguments: args,
      dimensions: (form.value.dimensionsText || "").split(",").map((item) => item.trim()).filter(Boolean),
      assertion_type: form.value.assertion_type,
      expected_value: form.value.expected_value || "",
      owner: form.value.owner || "",
      enabled: form.value.enabled,
    };
    if (editingCase.value?.id) {
      const updatePayload = { ...payload };
      delete updatePayload.metric_id;
      await updateValidationCase(editingCase.value.id, updatePayload);
      ElMessage.success(t("validation.updateSuccess"));
    } else {
      await createValidationCase(props.subjectId, payload);
      ElMessage.success(t("validation.createSuccess"));
    }
    dialogVisible.value = false;
    await loadCases();
  } catch (error) {
    notifyError(error, t("common.operationFailed"));
  } finally {
    saving.value = false;
  }
}

async function runCase(item: ValidationCaseVO) {
  runningId.value = item.id;
  try {
    const result = await runValidationCase(item.id);
    const type = result.last_status === "PASS" ? "success" : result.last_status === "FAIL" ? "warning" : "error";
    ElMessage({ type, message: result.last_message || result.last_status || t("validation.runFinished") });
    await loadCases();
  } catch (error) {
    notifyError(error, t("common.operationFailed"));
  } finally {
    runningId.value = "";
  }
}

async function removeCase(item: ValidationCaseVO) {
  try {
    await ElMessageBox.confirm(
      t("validation.deleteConfirm", { name: item.name }),
      t("common.warning"),
      { type: "warning", confirmButtonText: t("common.confirm"), cancelButtonText: t("common.cancel") },
    );
    await deleteValidationCase(item.id);
    ElMessage.success(t("validation.deleteSuccess"));
    await loadCases();
  } catch (error) {
    if (error !== "cancel") notifyError(error, t("common.operationFailed"));
  }
}

function openDetail(item: ValidationCaseVO) {
  detailCase.value = item;
  detailVisible.value = true;
}

function statusType(status?: string) {
  if (status === "PASS") return "success";
  if (status === "FAIL") return "warning";
  if (status === "ERROR") return "danger";
  return "info";
}

function metricLabel(metricId?: string) {
  const metric = metrics.value.find((item) => item.id === metricId);
  return metric ? `${metric.name} (${metric.code})` : metricId || "-";
}

function formatJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2);
}

watch(() => props.subjectId, () => {
  page.value = 1;
  loadMetrics();
  loadCases();
});

onMounted(() => {
  loadMetrics();
  loadCases();
});
</script>

<template>
  <div class="validation-workbench flex flex-col gap-4">
    <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
      <div>
        <div class="text-base font-semibold text-[var(--ep-text-color-primary)]">{{ t("validation.title") }}</div>
        <div class="mt-1 text-xs text-[var(--ep-text-color-secondary)]">{{ t("validation.subtitle") }}</div>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <el-button :icon="Refresh" @click="loadCases">{{ t("common.refresh") }}</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">{{ t("validation.createButton") }}</el-button>
      </div>
    </div>

    <div class="grid grid-cols-2 gap-3 md:grid-cols-4">
      <div class="rounded-xl border border-[var(--ep-border-color-lighter)] bg-[var(--ep-bg-color)] p-4">
        <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("validation.healthScore") }}</div>
        <div class="mt-2 text-2xl font-bold" :class="healthScore === 100 ? 'text-[var(--ep-color-success)]' : healthScore === null ? 'text-[var(--ep-text-color-secondary)]' : 'text-[var(--ep-color-warning)]'">
          {{ healthScore === null ? "-" : `${healthScore}%` }}
        </div>
      </div>
      <div class="rounded-xl border border-[var(--ep-border-color-lighter)] bg-[var(--ep-bg-color)] p-4">
        <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("validation.totalCases") }}</div>
        <div class="mt-2 text-2xl font-bold">{{ enabledCases.length }}</div>
      </div>
      <div class="rounded-xl border border-[var(--ep-border-color-lighter)] bg-[var(--ep-bg-color)] p-4">
        <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("validation.passCount") }}</div>
        <div class="mt-2 text-2xl font-bold text-[var(--ep-color-success)]">{{ passCount }}</div>
      </div>
      <div class="rounded-xl border border-[var(--ep-border-color-lighter)] bg-[var(--ep-bg-color)] p-4">
        <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("validation.issueCount") }}</div>
        <div class="mt-2 text-2xl font-bold text-[var(--ep-color-danger)]">{{ failCount + errorCount }}</div>
      </div>
    </div>

    <el-table v-loading="loading" :data="cases" border stripe class="w-full">
      <el-table-column prop="name" :label="t('common.name')" min-width="170" show-overflow-tooltip />
      <el-table-column prop="question" :label="t('validation.question')" min-width="240" show-overflow-tooltip />
      <el-table-column :label="t('validation.metric')" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ metricLabel(row.metric_id) }}</template>
      </el-table-column>
      <el-table-column :label="t('validation.status')" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.last_status)" size="small">{{ row.last_status || t("validation.notRun") }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('validation.lastRunAt')" width="165">
        <template #default="{ row }">{{ row.last_run_at ? formatDateTime(row.last_run_at) : "-" }}</template>
      </el-table-column>
      <el-table-column :label="t('validation.lastMessage')" min-width="210" show-overflow-tooltip>
        <template #default="{ row }">{{ row.last_message || "-" }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="230" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :loading="runningId === row.id" @click="runCase(row)">{{ t("validation.run") }}</el-button>
          <el-button type="primary" link @click="openDetail(row)">{{ t("validation.detail") }}</el-button>
          <el-button type="primary" link @click="openEdit(row)">{{ t("common.edit") }}</el-button>
          <el-button type="danger" link @click="removeCase(row)">{{ t("common.delete") }}</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="t('validation.emptyList')" :image-size="88" />
      </template>
    </el-table>

    <div class="flex justify-end">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="loadCases"
        @size-change="page = 1; loadCases()"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="editingCase ? t('validation.editTitle') : t('validation.createTitle')" width="680px" destroy-on-close>
      <el-form label-position="top">
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <el-form-item :label="t('common.name')" required>
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item :label="t('validation.metric')" required>
            <el-select v-model="form.metric_id" :disabled="Boolean(editingCase)" filterable class="w-full">
              <el-option v-for="option in metricOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item :label="t('validation.question')" required>
          <el-input v-model="form.question" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('validation.argumentsJson')">
          <el-input v-model="form.argumentsJson" type="textarea" :rows="7" class="font-mono" />
        </el-form-item>
        <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
          <el-form-item :label="t('validation.assertionType')">
            <el-select v-model="form.assertion_type" class="w-full">
              <el-option label="EXECUTION_SUCCESS" value="EXECUTION_SUCCESS" />
              <el-option label="NON_EMPTY" value="NON_EMPTY" />
              <el-option label="ROW_COUNT" value="ROW_COUNT" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('validation.expectedValue')">
            <el-input v-model="form.expected_value" :disabled="form.assertion_type !== 'ROW_COUNT'" />
          </el-form-item>
          <el-form-item :label="t('validation.owner')">
            <el-input v-model="form.owner" />
          </el-form-item>
        </div>
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <el-form-item :label="t('validation.dimensions')">
            <el-input v-model="form.dimensionsText" placeholder="org_id, workshop_id" />
          </el-form-item>
          <el-form-item :label="t('validation.enabled')">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </div>
        <el-form-item :label="t('common.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveCase">{{ t("common.save") }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="detailCase?.name" size="min(720px, 100vw)">
      <div v-if="detailCase" class="flex flex-col gap-4">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('validation.status')">
            <el-tag :type="statusType(detailCase.last_status)" size="small">{{ detailCase.last_status || t("validation.notRun") }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('validation.lastRunAt')">{{ detailCase.last_run_at ? formatDateTime(detailCase.last_run_at) : "-" }}</el-descriptions-item>
          <el-descriptions-item :label="t('validation.metric')" :span="2">{{ metricLabel(detailCase.metric_id) }}</el-descriptions-item>
          <el-descriptions-item :label="t('validation.question')" :span="2">{{ detailCase.question }}</el-descriptions-item>
          <el-descriptions-item :label="t('validation.lastMessage')" :span="2">{{ detailCase.last_message || "-" }}</el-descriptions-item>
          <el-descriptions-item :label="t('validation.lastMetricRevision')">{{ detailCase.last_metric_revision ? `v${detailCase.last_metric_revision}` : "-" }}</el-descriptions-item>
          <el-descriptions-item :label="t('validation.duration')">{{ detailCase.last_duration_ms ? `${detailCase.last_duration_ms}ms` : "-" }}</el-descriptions-item>
        </el-descriptions>
        <div>
          <div class="mb-2 text-sm font-semibold">{{ t("validation.evidence") }}</div>
          <pre class="max-h-80 overflow-auto rounded-lg bg-[var(--ep-fill-color)] p-3 text-xs leading-5">{{ formatJson(detailCase.last_evidence) }}</pre>
        </div>
        <div>
          <div class="mb-2 text-sm font-semibold">{{ t("validation.argumentsJson") }}</div>
          <pre class="max-h-48 overflow-auto rounded-lg bg-[var(--ep-fill-color)] p-3 text-xs leading-5">{{ formatJson(detailCase.arguments) }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>
