<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import type { TableInfoVO } from "~/api/subject";
import type { TableRelationRecommendationVO, TableRelationVO } from "~/api/table-relation";

type EdgeKind = "confirmed" | "physical" | "inferred";
interface GraphEdge {
  key: string;
  kind: EdgeKind;
  factTableId: string;
  factTableName: string;
  factColumn: string;
  dimensionTableId: string;
  dimensionTableName: string;
  dimensionColumn: string;
  cardinality: string;
  confidence?: string;
  score?: number;
  evidence?: string[];
  recommendation?: TableRelationRecommendationVO;
}

const props = defineProps<{
  tables: TableInfoVO[];
  relations: TableRelationVO[];
  recommendations: TableRelationRecommendationVO[];
  loading?: boolean;
}>();
const emit = defineEmits<{
  confirm: [item: TableRelationRecommendationVO];
  batchConfirm: [items: TableRelationRecommendationVO[]];
}>();
const { t } = useI18n();
const selectedKey = ref("");
const batchKeys = ref<Set<string>>(new Set());

const nodePositions = computed(() => {
  const result = new Map<string, { x: number; y: number }>();
  const columns = Math.max(2, Math.ceil(Math.sqrt(props.tables.length || 1)));
  props.tables.forEach((table, index) => {
    result.set(table.id, { x: 90 + (index % columns) * 235, y: 70 + Math.floor(index / columns) * 150 });
  });
  return result;
});
const graphHeight = computed(() => Math.max(360, 120 + Math.ceil((props.tables.length || 1) / Math.max(2, Math.ceil(Math.sqrt(props.tables.length || 1)))) * 150));

function relationKey(parts: { factTableId: string; factColumn: string; dimensionTableId: string; dimensionColumn: string }) {
  return `${parts.factTableId}|${parts.factColumn}|${parts.dimensionTableId}|${parts.dimensionColumn}`;
}

const edges = computed<GraphEdge[]>(() => {
  const confirmed = new Set(props.relations.map((item) => relationKey({
    factTableId: item.fact_table_id,
    factColumn: item.fact_column,
    dimensionTableId: item.dimension_table_id,
    dimensionColumn: item.dimension_column,
  })));
  const rows: GraphEdge[] = props.relations.map((item) => ({
    key: relationKey({ factTableId: item.fact_table_id, factColumn: item.fact_column, dimensionTableId: item.dimension_table_id, dimensionColumn: item.dimension_column }),
    kind: "confirmed",
    factTableId: item.fact_table_id,
    factTableName: item.fact_table_name || tableName(item.fact_table_id),
    factColumn: item.fact_column,
    dimensionTableId: item.dimension_table_id,
    dimensionTableName: item.dimension_table_name || tableName(item.dimension_table_id),
    dimensionColumn: item.dimension_column,
    cardinality: item.cardinality || "MANY_TO_ONE",
  }));
  for (const item of props.recommendations) {
    const key = relationKey({ factTableId: item.fact_table_id, factColumn: item.fact_column, dimensionTableId: item.dimension_table_id, dimensionColumn: item.dimension_column });
    if (confirmed.has(key)) continue;
    rows.push({
      key,
      kind: item.source === "FOREIGN_KEY" ? "physical" : "inferred",
      factTableId: item.fact_table_id,
      factTableName: item.fact_table_name || tableName(item.fact_table_id),
      factColumn: item.fact_column,
      dimensionTableId: item.dimension_table_id,
      dimensionTableName: item.dimension_table_name || tableName(item.dimension_table_id),
      dimensionColumn: item.dimension_column,
      cardinality: item.cardinality || "MANY_TO_ONE",
      confidence: item.confidence,
      score: item.score,
      evidence: item.evidence,
      recommendation: item,
    });
  }
  return rows;
});
const selectedEdge = computed(() => edges.value.find((edge) => edge.key === selectedKey.value) || null);
const batchEdges = computed(() => edges.value.filter((edge) => batchKeys.value.has(edge.key) && edge.recommendation));
const stats = computed(() => ({
  physical: edges.value.filter((edge) => edge.kind === "physical").length,
  inferred: edges.value.filter((edge) => edge.kind === "inferred").length,
  confirmed: edges.value.filter((edge) => edge.kind === "confirmed").length,
}));

function tableName(id: string) {
  return props.tables.find((table) => table.id === id)?.name || id.slice(0, 8);
}
function center(id: string) {
  const pos = nodePositions.value.get(id) || { x: 0, y: 0 };
  return { x: pos.x + 82, y: pos.y + 32 };
}
function edgeColor(edge: GraphEdge) {
  if (edge.kind === "confirmed") return "var(--ep-color-primary)";
  if (edge.kind === "physical") return "var(--ep-color-success)";
  return "var(--ep-color-warning)";
}
function edgeLabel(edge: GraphEdge) {
  return `${edge.factColumn} → ${edge.dimensionColumn}`;
}
function selectEdge(edge: GraphEdge) {
  selectedKey.value = edge.key;
}
function evidenceLabel(code: string) {
  return t(`relation.evidenceLabels.${code}`);
}
function toggleBatch(edge: GraphEdge) {
  if (!edge.recommendation) return;
  const next = new Set(batchKeys.value);
  if (next.has(edge.key)) next.delete(edge.key); else next.add(edge.key);
  batchKeys.value = next;
}
function clearBatch() {
  batchKeys.value = new Set();
}
function confirm(edge: GraphEdge) {
  if (edge.recommendation) emit("confirm", edge.recommendation);
}
function confirmBatch() {
  emit("batchConfirm", batchEdges.value.map((edge) => edge.recommendation!));
  clearBatch();
}
</script>

<template>
  <div class="flex flex-col gap-4" v-loading="loading ?? false">
    <div class="grid grid-cols-2 gap-3 md:grid-cols-4">
      <div class="rounded-lg border border-[var(--ep-border-color-lighter)] p-3">
        <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.graphTables") }}</div>
        <div class="mt-1 text-xl font-semibold">{{ tables.length }}</div>
      </div>
      <div class="rounded-lg border border-[var(--ep-border-color-lighter)] p-3">
        <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.graphPhysical") }}</div>
        <div class="mt-1 text-xl font-semibold text-[var(--ep-color-success)]">{{ stats.physical }}</div>
      </div>
      <div class="rounded-lg border border-[var(--ep-border-color-lighter)] p-3">
        <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.graphInferred") }}</div>
        <div class="mt-1 text-xl font-semibold text-[var(--ep-color-warning)]">{{ stats.inferred }}</div>
      </div>
      <div class="rounded-lg border border-[var(--ep-border-color-lighter)] p-3">
        <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.graphConfirmed") }}</div>
        <div class="mt-1 text-xl font-semibold text-[var(--ep-color-primary)]">{{ stats.confirmed }}</div>
      </div>
    </div>

    <div class="flex flex-col gap-4 xl:flex-row">
      <div class="min-h-[360px] flex-1 overflow-auto rounded-xl border border-[var(--ep-border-color-lighter)] bg-[var(--ep-fill-color-extra-light)]">
        <svg :width="Math.max(760, Math.ceil(tables.length / 3) * 235)" :height="graphHeight" class="block">
          <defs>
            <marker id="relation-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
              <path d="M0,0 L8,4 L0,8 z" fill="currentColor" />
            </marker>
          </defs>
          <g v-for="edge in edges" :key="edge.key" class="cursor-pointer" @click="selectEdge(edge)">
            <line
              :x1="center(edge.factTableId).x" :y1="center(edge.factTableId).y"
              :x2="center(edge.dimensionTableId).x" :y2="center(edge.dimensionTableId).y"
              :stroke="edgeColor(edge)" :stroke-width="selectedKey === edge.key ? 4 : 2"
              :stroke-dasharray="edge.kind === 'inferred' ? '7 5' : undefined"
              marker-end="url(#relation-arrow)" opacity="0.86"
            />
            <line
              :x1="center(edge.factTableId).x" :y1="center(edge.factTableId).y"
              :x2="center(edge.dimensionTableId).x" :y2="center(edge.dimensionTableId).y"
              stroke="transparent" stroke-width="16"
            />
            <text
              :x="(center(edge.factTableId).x + center(edge.dimensionTableId).x) / 2"
              :y="(center(edge.factTableId).y + center(edge.dimensionTableId).y) / 2 - 5"
              text-anchor="middle" class="fill-[var(--ep-text-color-secondary)] text-[11px]"
            >{{ edgeLabel(edge) }}</text>
          </g>
          <g v-for="table in tables" :key="table.id" class="cursor-pointer">
            <rect
              :x="nodePositions.get(table.id)?.x" :y="nodePositions.get(table.id)?.y"
              width="164" height="64" rx="10"
              fill="var(--ep-bg-color)" stroke="var(--ep-border-color)" stroke-width="1.2"
            />
            <text :x="(nodePositions.get(table.id)?.x || 0) + 82" :y="(nodePositions.get(table.id)?.y || 0) + 26" text-anchor="middle" class="fill-[var(--ep-text-color-primary)] text-[13px] font-semibold">
              {{ table.name.length > 18 ? table.name.slice(0, 18) + "…" : table.name }}
            </text>
            <text :x="(nodePositions.get(table.id)?.x || 0) + 82" :y="(nodePositions.get(table.id)?.y || 0) + 46" text-anchor="middle" class="fill-[var(--ep-text-color-secondary)] text-[11px]">
              {{ table.description || t("relation.graphTableNode") }}
            </text>
          </g>
        </svg>
        <el-empty v-if="!tables.length && !loading" :description="t('relation.emptyList')" :image-size="90" />
      </div>

      <div class="w-full shrink-0 rounded-xl border border-[var(--ep-border-color-lighter)] p-4 xl:w-[340px]">
        <div class="mb-3 flex items-center justify-between">
          <div class="font-semibold">{{ t("relation.inspector") }}</div>
          <el-tag v-if="selectedEdge" :type="selectedEdge.kind === 'physical' ? 'success' : selectedEdge.kind === 'confirmed' ? 'primary' : 'warning'" size="small">
            {{ t(`relation.graphKind.${selectedEdge.kind}`) }}
          </el-tag>
        </div>
        <template v-if="selectedEdge">
          <div class="space-y-3 text-sm">
            <div>
              <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.factTable") }}</div>
              <div class="mt-1 font-medium">{{ selectedEdge.factTableName }}</div>
              <code class="text-xs">{{ selectedEdge.factColumn }}</code>
            </div>
            <div>
              <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.dimensionTable") }}</div>
              <div class="mt-1 font-medium">{{ selectedEdge.dimensionTableName }}</div>
              <code class="text-xs">{{ selectedEdge.dimensionColumn }}</code>
            </div>
            <div>
              <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.cardinality") }}</div>
              <el-select v-if="selectedEdge.recommendation" v-model="selectedEdge.recommendation.cardinality" size="small" class="mt-1 w-full">
                <el-option v-for="item in ['MANY_TO_ONE','ONE_TO_ONE','ONE_TO_MANY','MANY_TO_MANY']" :key="item" :label="item" :value="item" />
              </el-select>
              <el-tag v-else size="small" class="mt-1">{{ selectedEdge.cardinality }}</el-tag>
            </div>
            <div v-if="selectedEdge.confidence" class="flex gap-4 text-xs">
              <span>{{ t("relation.confidence") }}: {{ selectedEdge.confidence }}</span>
              <span>{{ t("relation.score") }}: {{ selectedEdge.score }}</span>
            </div>
            <div v-if="selectedEdge.evidence?.length" class="flex flex-wrap gap-1">
              <el-tag v-for="code in selectedEdge.evidence" :key="code" size="small" effect="plain">{{ evidenceLabel(code) }}</el-tag>
            </div>
          </div>
          <div v-if="selectedEdge.recommendation" class="mt-5 flex flex-col gap-2">
            <el-button type="primary" @click="confirm(selectedEdge)">{{ t("relation.confirm") }}</el-button>
            <el-button @click="toggleBatch(selectedEdge)">
              {{ batchKeys.has(selectedEdge.key) ? t("relation.removeBatch") : t("relation.addBatch") }}
            </el-button>
          </div>
        </template>
        <el-empty v-else :description="t('relation.selectEdgeTip')" :image-size="86" />

        <div class="mt-5 border-t border-[var(--ep-border-color-lighter)] pt-4">
          <div class="mb-2 text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.batchTray", { count: batchEdges.length }) }}</div>
          <div class="flex gap-2">
            <el-button type="primary" size="small" :disabled="!batchEdges.length" @click="confirmBatch">{{ t("relation.batchConfirm", { count: batchEdges.length }) }}</el-button>
            <el-button size="small" :disabled="!batchEdges.length" @click="clearBatch">{{ t("common.reset") }}</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
