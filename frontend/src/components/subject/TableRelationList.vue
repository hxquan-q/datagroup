<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, RefreshCw as Refresh, WandSparkles } from "@lucide/vue";
import { getSubjectTables, type TableInfoVO } from "~/api/subject";
import type { TableColumnVO } from "~/api/column";
import type { TableRelationPayload, TableRelationRecommendationVO, TableRelationVO } from "~/api/table-relation";
import { createTableRelation, deleteTableRelation, listRelationColumns, listTableRelationRecommendations, listTableRelations, updateTableRelation } from "~/api/table-relation";
import { notifyError } from "~/api/http";
import { formatDateTime } from "~/composables";

const props = defineProps<{ subjectId: string; datasourceId: string }>();
const { t } = useI18n();
const loading = ref(false);
const saving = ref(false);
const relations = ref<TableRelationVO[]>([]);
const tables = ref<TableInfoVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const dialogVisible = ref(false);
const editing = ref<TableRelationVO | null>(null);
const factColumns = ref<TableColumnVO[]>([]);
const dimensionColumns = ref<TableColumnVO[]>([]);
const form = ref<TableRelationPayload>({ cardinality: "MANY_TO_ONE" });
const recommendVisible = ref(false);
const recommending = ref(false);
const confirmingKey = ref("");
const recommendations = ref<TableRelationRecommendationVO[]>([]);
const tableOptions = computed(() => tables.value.map((table) => ({ label: table.name, value: table.id })));
const cardinalityOptions = ["MANY_TO_ONE", "ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_MANY"];

async function loadRelations() {
  loading.value = true;
  try {
    const resp = await listTableRelations(props.subjectId, page.value, size.value);
    relations.value = resp.data;
    total.value = resp.total;
  } catch (error) { notifyError(error, t("common.loadFailed")); }
  finally { loading.value = false; }
}

async function loadTables() {
  try {
    const first = await getSubjectTables(props.subjectId, 1, 100);
    const items = [...first.data];
    const pages = Math.ceil((first.total || 0) / 100) - 1;
    for (let next = 2; next <= pages + 1; next += 1) {
      items.push(...(await getSubjectTables(props.subjectId, next, 100)).data);
    }
    tables.value = items;
  } catch (error) { notifyError(error, t("common.loadFailed")); }
}

async function loadColumnOptions() {
  try {
    if (form.value.fact_table_id) factColumns.value = (await listRelationColumns(props.subjectId, form.value.fact_table_id));
    if (form.value.dimension_table_id) dimensionColumns.value = (await listRelationColumns(props.subjectId, form.value.dimension_table_id));
  } catch (error) { notifyError(error, t("common.loadFailed")); }
}

function openCreate() {
  editing.value = null;
  form.value = { cardinality: "MANY_TO_ONE" };
  factColumns.value = [];
  dimensionColumns.value = [];
  dialogVisible.value = true;
}

async function openEdit(row: TableRelationVO) {
  editing.value = row;
  form.value = {
    fact_table_id: row.fact_table_id,
    fact_column: row.fact_column,
    dimension_table_id: row.dimension_table_id,
    dimension_column: row.dimension_column,
    cardinality: row.cardinality,
    owner: row.owner,
    description: row.description ?? undefined,
  };
  dialogVisible.value = true;
  await loadColumnOptions();
}

async function tableChanged() {
  if (!editing.value) {
    form.value.fact_column = "";
    form.value.dimension_column = "";
  }
  await loadColumnOptions();
}

async function save() {
  if (!form.value.fact_table_id || !form.value.dimension_table_id || !form.value.fact_column || !form.value.dimension_column) {
    ElMessage.warning(t("relation.requiredFields"));
    return;
  }
  saving.value = true;
  try {
    const payload: TableRelationPayload = { ...form.value };
    if (editing.value) {
      delete payload.fact_table_id;
      delete payload.dimension_table_id;
      await updateTableRelation(editing.value.id, payload);
      ElMessage.success(t("relation.updateSuccess"));
    } else {
      await createTableRelation(props.subjectId, payload);
      ElMessage.success(t("relation.createSuccess"));
    }
    dialogVisible.value = false;
    await loadRelations();
  } catch (error) { notifyError(error, t("common.operationFailed")); }
  finally { saving.value = false; }
}

function recommendationKey(item: TableRelationRecommendationVO) {
  return `${item.fact_table_id}|${item.fact_column}|${item.dimension_table_id}|${item.dimension_column}`;
}

function confidenceType(confidence?: string) {
  if (confidence === "HIGH") return "success";
  if (confidence === "MEDIUM") return "warning";
  return "info";
}

function evidenceLabel(code: string) {
  return t(`relation.evidenceLabels.${code}`);
}

async function openRecommendations() {
  recommendVisible.value = true;
  recommending.value = true;
  try {
    recommendations.value = await listTableRelationRecommendations(props.subjectId, 50);
  } catch (error) { notifyError(error, t("common.loadFailed")); }
  finally { recommending.value = false; }
}

async function confirmRecommendation(item: TableRelationRecommendationVO) {
  const key = recommendationKey(item);
  confirmingKey.value = key;
  try {
    await createTableRelation(props.subjectId, {
      fact_table_id: item.fact_table_id,
      fact_column: item.fact_column,
      dimension_table_id: item.dimension_table_id,
      dimension_column: item.dimension_column,
      cardinality: item.cardinality || "MANY_TO_ONE",
    });
    recommendations.value = recommendations.value.filter((candidate) => recommendationKey(candidate) !== key);
    ElMessage.success(t("relation.confirmSuccess"));
    await loadRelations();
  } catch (error) { notifyError(error, t("common.operationFailed")); }
  finally { confirmingKey.value = ""; }
}

async function remove(row: TableRelationVO) {
  try {
    await ElMessageBox.confirm(t("relation.deleteConfirm"), t("common.warning"), { type: "warning" });
    await deleteTableRelation(row.id);
    ElMessage.success(t("relation.deleteSuccess"));
    await loadRelations();
  } catch (error) { if (error !== "cancel") notifyError(error, t("common.operationFailed")); }
}

onMounted(() => { loadTables(); loadRelations(); });
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
      <div>
        <div class="text-base font-semibold">{{ t("relation.title") }}</div>
        <div class="mt-1 text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.subtitle") }}</div>
      </div>
      <div class="flex flex-wrap gap-2">
        <el-button :icon="Refresh" @click="loadRelations">{{ t("common.refresh") }}</el-button>
        <el-button :icon="WandSparkles" @click="openRecommendations">{{ t("relation.recommendButton") }}</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">{{ t("relation.createButton") }}</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="relations" border stripe class="w-full">
      <el-table-column :label="t('relation.factTable')" min-width="170">
        <template #default="{ row }">{{ row.fact_table_name || row.fact_table_id }}</template>
      </el-table-column>
      <el-table-column prop="fact_column" :label="t('relation.factColumn')" min-width="140" />
      <el-table-column :label="t('relation.dimensionTable')" min-width="170">
        <template #default="{ row }">{{ row.dimension_table_name || row.dimension_table_id }}</template>
      </el-table-column>
      <el-table-column prop="dimension_column" :label="t('relation.dimensionColumn')" min-width="140" />
      <el-table-column prop="cardinality" :label="t('relation.cardinality')" width="140" align="center">
        <template #default="{ row }"><el-tag size="small">{{ row.cardinality }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="owner" :label="t('relation.owner')" width="110" />
      <el-table-column :label="t('metric.revision')" width="85" align="center">
        <template #default="{ row }">v{{ row.revision || 1 }}</template>
      </el-table-column>
      <el-table-column :label="t('common.updatedAt')" width="165">
        <template #default="{ row }">{{ row.updated_at ? formatDateTime(row.updated_at) : "-" }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="140" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openEdit(row)">{{ t("common.edit") }}</el-button>
          <el-button type="danger" link @click="remove(row)">{{ t("common.delete") }}</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty :description="t('relation.emptyList')" :image-size="88" /></template>
    </el-table>

    <div class="flex justify-end">
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, prev, pager, next" background @current-change="loadRelations" />
    </div>

    <el-dialog v-model="recommendVisible" :title="t('relation.recommendTitle')" width="min(920px, calc(100vw - 32px))" destroy-on-close>
      <div class="mb-3 text-xs leading-5 text-[var(--ep-text-color-secondary)]">{{ t("relation.recommendSubtitle") }}</div>
      <el-table v-loading="recommending" :data="recommendations" border stripe max-height="520" class="w-full">
        <el-table-column :label="t('relation.factTable')" min-width="170">
          <template #default="{ row }">
            <div class="font-medium">{{ row.fact_table_name || row.fact_table_id }}</div>
            <div class="font-mono text-xs text-[var(--ep-text-color-secondary)]">{{ row.fact_column }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('relation.dimensionTable')" min-width="170">
          <template #default="{ row }">
            <div class="font-medium">{{ row.dimension_table_name || row.dimension_table_id }}</div>
            <div class="font-mono text-xs text-[var(--ep-text-color-secondary)]">{{ row.dimension_column }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('relation.source')" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="row.source === 'FOREIGN_KEY' ? 'success' : 'info'" size="small" effect="plain">{{ row.source === 'FOREIGN_KEY' ? t('relation.sourcePhysical') : t('relation.sourceInferred') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('relation.confidence')" width="105" align="center">
          <template #default="{ row }"><el-tag :type="confidenceType(row.confidence)" size="small">{{ row.confidence || "-" }}</el-tag></template>
        </el-table-column>
        <el-table-column :label="t('relation.score')" width="80" align="center">
          <template #default="{ row }">{{ row.score ?? "-" }}</template>
        </el-table-column>
        <el-table-column :label="t('relation.evidence')" min-width="230">
          <template #default="{ row }">
            <div class="flex flex-wrap gap-1">
              <el-tag v-for="code in row.evidence || []" :key="code" size="small" effect="plain">{{ evidenceLabel(code) }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('relation.cardinality')" width="155">
          <template #default="{ row }">
            <el-select v-model="row.cardinality" size="small" class="w-full">
              <el-option v-for="item in cardinalityOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="105" align="center">
          <template #default="{ row }">
            <el-button type="primary" link :loading="confirmingKey === recommendationKey(row)" @click="confirmRecommendation(row)">{{ t("relation.confirm") }}</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="t('relation.emptyRecommendations')" :image-size="88" /></template>
      </el-table>
    </el-dialog>

    <el-dialog v-model="dialogVisible" :title="editing ? t('relation.editTitle') : t('relation.createTitle')" width="min(640px, calc(100vw - 32px))" destroy-on-close>
      <el-form label-position="top">
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <el-form-item :label="t('relation.factTable')" required>
            <el-select v-model="form.fact_table_id" :disabled="Boolean(editing)" filterable class="w-full" @change="tableChanged">
              <el-option v-for="item in tableOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('relation.dimensionTable')" required>
            <el-select v-model="form.dimension_table_id" :disabled="Boolean(editing)" filterable class="w-full" @change="tableChanged">
              <el-option v-for="item in tableOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('relation.factColumn')" required>
            <el-select v-model="form.fact_column" filterable allow-create default-first-option class="w-full">
              <el-option v-for="col in factColumns" :key="col.id || col.name" :label="col.name" :value="col.name" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('relation.dimensionColumn')" required>
            <el-select v-model="form.dimension_column" filterable allow-create default-first-option class="w-full">
              <el-option v-for="col in dimensionColumns" :key="col.id || col.name" :label="col.name" :value="col.name" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('relation.cardinality')">
            <el-select v-model="form.cardinality" class="w-full">
              <el-option v-for="item in cardinalityOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('relation.owner')"><el-input v-model="form.owner" /></el-form-item>
        </div>
        <el-form-item :label="t('common.description')"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t("common.save") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
