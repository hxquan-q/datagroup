<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Search } from "@element-plus/icons-vue";
import type { MetricVO } from "~/api/metric";
import { deleteMetric, listMetrics } from "~/api/metric";
import { notifyError } from "~/api/http";
import { formatDateTime } from "~/composables";

const props = defineProps<{ subjectId: string }>();
const { t } = useI18n();

const loading = ref(false);
const metrics = ref<MetricVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const keyword = ref("");
const wizardVisible = ref(false);
const editingMetric = ref<MetricVO | null>(null);

async function loadMetrics() {
  loading.value = true;
  try {
    const resp = await listMetrics(props.subjectId, page.value, size.value, keyword.value || undefined);
    metrics.value = resp.data;
    total.value = resp.total;
  } catch (error) {
    notifyError(error, t("common.loadFailed"));
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingMetric.value = null;
  wizardVisible.value = true;
}

function openEdit(metric: MetricVO) {
  editingMetric.value = metric;
  wizardVisible.value = true;
}

async function removeMetric(metric: MetricVO) {
  try {
    await ElMessageBox.confirm(
      t("metric.deleteConfirm", { name: metric.name }),
      t("common.warning"),
      { type: "warning", confirmButtonText: t("common.confirm"), cancelButtonText: t("common.cancel") },
    );
    await deleteMetric(metric.id);
    ElMessage.success(t("metric.deleteSuccess"));
    await loadMetrics();
  } catch (error) {
    if (error !== "cancel") notifyError(error, t("common.operationFailed"));
  }
}

function handleSaved() {
  loadMetrics();
}

onMounted(loadMetrics);
</script>

<template>
  <div class="metric-list flex flex-col gap-4">
    <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
      <div>
        <div class="text-base font-semibold text-[var(--ep-text-color-primary)]">{{ t("metric.title") }}</div>
        <div class="mt-1 text-xs text-[var(--ep-text-color-secondary)]">{{ t("metric.subtitle") }}</div>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <el-input
          v-model="keyword"
          :placeholder="t('metric.searchPlaceholder')"
          clearable
          class="w-64"
          :prefix-icon="Search"
          @keyup.enter="page = 1; loadMetrics()"
          @clear="page = 1; loadMetrics()"
        />
        <el-button type="primary" :icon="Plus" @click="openCreate">{{ t("metric.createButton") }}</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="metrics" border stripe class="w-full">
      <el-table-column prop="code" :label="t('metric.code')" min-width="170">
        <template #default="{ row }">
          <code class="rounded bg-[var(--ep-fill-color)] px-1.5 py-0.5 text-xs">{{ row.code }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="name" :label="t('common.name')" min-width="180" show-overflow-tooltip />
      <el-table-column prop="owner" :label="t('metric.owner')" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.owner || "-" }}</template>
      </el-table-column>
      <el-table-column prop="grain" :label="t('metric.grain')" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.grain || "-" }}</template>
      </el-table-column>
      <el-table-column :label="t('metric.allowedDimensions')" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.allowed_dimensions?.join(", ") || "-" }}</template>
      </el-table-column>
      <el-table-column :label="t('metric.revision')" width="90" align="center">
        <template #default="{ row }">v{{ row.revision || 1 }}</template>
      </el-table-column>
      <el-table-column :label="t('common.updatedAt')" width="170">
        <template #default="{ row }">{{ row.updated_at ? formatDateTime(row.updated_at) : "-" }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openEdit(row)">{{ t("common.edit") }}</el-button>
          <el-button type="danger" link @click="removeMetric(row)">{{ t("common.delete") }}</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="t('metric.emptyList')" :image-size="88" />
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
        @current-change="loadMetrics"
        @size-change="page = 1; loadMetrics()"
      />
    </div>

    <MetricWizard v-model:visible="wizardVisible" :subject-id="subjectId" :metric="editingMetric" @saved="handleSaved" />
  </div>
</template>
