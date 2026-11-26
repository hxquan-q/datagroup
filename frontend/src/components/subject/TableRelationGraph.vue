<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ZoomIn, ZoomOut, RotateCcw, Sparkles } from "@lucide/vue";
import type { TableInfoVO } from "~/api/subject";
import type { TableRelationRecommendationVO, TableRelationVO } from "~/api/table-relation";

type EdgeKind = "confirmed" | "physical" | "inferred";
interface GraphEdge {
  key: string; kind: EdgeKind; factTableId: string; factTableName: string; factColumn: string;
  dimensionTableId: string; dimensionTableName: string; dimensionColumn: string; cardinality: string;
  confidence?: string; score?: number; evidence?: string[]; recommendation?: TableRelationRecommendationVO;
}
interface NodeBox { x: number; y: number; width: number; height: number }

const props = defineProps<{ tables: TableInfoVO[]; relations: TableRelationVO[]; recommendations: TableRelationRecommendationVO[]; loading?: boolean }>();
const emit = defineEmits<{ confirm: [item: TableRelationRecommendationVO]; batchConfirm: [items: TableRelationRecommendationVO[]] }>();
const { t } = useI18n();
const selectedKey = ref("");
const focusedTableId = ref("");
const batchKeys = ref<Set<string>>(new Set());
const zoom = ref(1);
const panning = ref(false);
let panStart = { x: 0, y: 0, panX: 0, panY: 0 };
const panX = ref(0);
const panY = ref(0);
const layoutVersion = ref(0);
const nodeWidth = 190;

function keyOf(x: { factTableId: string; factColumn: string; dimensionTableId: string; dimensionColumn: string }) {
  return `${x.factTableId}|${x.factColumn}|${x.dimensionTableId}|${x.dimensionColumn}`;
}
function tableName(id: string) { return props.tables.find((table) => table.id === id)?.name || id.slice(0, 8); }
const edges = computed<GraphEdge[]>(() => {
  const rows: GraphEdge[] = props.relations.map((item) => ({
    key: keyOf({ factTableId: item.fact_table_id, factColumn: item.fact_column, dimensionTableId: item.dimension_table_id, dimensionColumn: item.dimension_column }),
    kind: "confirmed", factTableId: item.fact_table_id, factTableName: item.fact_table_name || tableName(item.fact_table_id), factColumn: item.fact_column,
    dimensionTableId: item.dimension_table_id, dimensionTableName: item.dimension_table_name || tableName(item.dimension_table_id), dimensionColumn: item.dimension_column,
    cardinality: item.cardinality || "MANY_TO_ONE",
  }));
  const confirmed = new Set(rows.map((item) => item.key));
  for (const item of props.recommendations) {
    const key = keyOf({ factTableId: item.fact_table_id, factColumn: item.fact_column, dimensionTableId: item.dimension_table_id, dimensionColumn: item.dimension_column });
    if (confirmed.has(key)) continue;
    rows.push({
      key, kind: item.source === "FOREIGN_KEY" ? "physical" : "inferred",
      factTableId: item.fact_table_id, factTableName: item.fact_table_name || tableName(item.fact_table_id), factColumn: item.fact_column,
      dimensionTableId: item.dimension_table_id, dimensionTableName: item.dimension_table_name || tableName(item.dimension_table_id), dimensionColumn: item.dimension_column,
      cardinality: item.cardinality || "MANY_TO_ONE", confidence: item.confidence, score: item.score, evidence: item.evidence, recommendation: item,
    });
  }
  return rows;
});

const tableColumns = computed(() => {
  const result = new Map<string, string[]>();
  for (const edge of edges.value) {
    for (const [tableId, column] of [[edge.factTableId, edge.factColumn], [edge.dimensionTableId, edge.dimensionColumn]] as const) {
      const list = result.get(tableId) || [];
      if (!list.includes(column)) list.push(column);
      result.set(tableId, list);
    }
  }
  return result;
});
function heightFor(tableId: string) { return 48 + Math.min(3, (tableColumns.value.get(tableId) || []).length) * 22 + ((tableColumns.value.get(tableId) || []).length > 3 ? 18 : 0); }

const layout = computed(() => {
  layoutVersion.value;
  const boxes = new Map<string, NodeBox>();
  const n = props.tables.length;
  if (!n) return { boxes, width: 900, height: 460 };
  const radius = Math.max(190, n * 28);
  const cx = radius + 180;
  const cy = radius + 130;
  const pos = props.tables.map((table, i) => {
    const angle = (Math.PI * 2 * i) / n + layoutVersion.value * 0.31;
    return { id: table.id, x: cx + Math.cos(angle) * radius, y: cy + Math.sin(angle) * radius * 0.72, vx: 0, vy: 0 };
  });
  const index = new Map(pos.map((item, i) => [item.id, i]));
  for (let iter = 0; iter < 110; iter += 1) {
    for (let i = 0; i < n; i += 1) {
      for (let j = i + 1; j < n; j += 1) {
        let dx = pos[i].x - pos[j].x; let dy = pos[i].y - pos[j].y;
        let dist = Math.max(40, Math.hypot(dx, dy));
        const force = Math.min(8, 12000 / (dist * dist));
        dx /= dist; dy /= dist;
        pos[i].vx += dx * force; pos[i].vy += dy * force; pos[j].vx -= dx * force; pos[j].vy -= dy * force;
      }
    }
    for (const edge of edges.value) {
      const a = index.get(edge.factTableId); const b = index.get(edge.dimensionTableId);
      if (a == null || b == null) continue;
      const dx = pos[b].x - pos[a].x; const dy = pos[b].y - pos[a].y;
      const dist = Math.max(1, Math.hypot(dx, dy));
      const force = (dist - 235) * 0.012;
      pos[a].vx += (dx / dist) * force; pos[a].vy += (dy / dist) * force;
      pos[b].vx -= (dx / dist) * force; pos[b].vy -= (dy / dist) * force;
    }
    for (const item of pos) {
      item.vx += (cx - item.x) * 0.0015; item.vy += (cy - item.y) * 0.0015;
      item.x += Math.max(-8, Math.min(8, item.vx)); item.y += Math.max(-8, Math.min(8, item.vy));
      item.vx *= 0.62; item.vy *= 0.62;
    }
  }
  for (let pass = 0; pass < 70; pass += 1) {
    let moved = false;
    for (let i = 0; i < n; i += 1) {
      for (let j = i + 1; j < n; j += 1) {
        const a = pos[i]; const b = pos[j];
        const ah = heightFor(props.tables[i].id); const bh = heightFor(props.tables[j].id);
        const dx = b.x - a.x; const dy = b.y - a.y;
        const overlapX = (nodeWidth + nodeWidth) / 2 + 34 - Math.abs(dx);
        const overlapY = (ah + bh) / 2 + 26 - Math.abs(dy);
        if (overlapX <= 0 || overlapY <= 0) continue;
        moved = true;
        if (overlapX < overlapY) {
          const push = overlapX / 2 + 1;
          const direction = dx >= 0 ? 1 : -1;
          a.x -= direction * push; b.x += direction * push;
        } else {
          const push = overlapY / 2 + 1;
          const direction = dy >= 0 ? 1 : -1;
          a.y -= direction * push; b.y += direction * push;
        }
      }
    }
    if (!moved) break;
  }
  const halfWidths = pos.map(() => nodeWidth / 2);
  const halfHeights = pos.map((item) => heightFor(item.id) / 2);
  const minX = Math.min(...pos.map((item, i) => item.x - halfWidths[i])) - 90;
  const minY = Math.min(...pos.map((item, i) => item.y - halfHeights[i])) - 70;
  const maxX = Math.max(...pos.map((item, i) => item.x + halfWidths[i])) + 90;
  const maxY = Math.max(...pos.map((item, i) => item.y + halfHeights[i])) + 80;
  pos.forEach((item, i) => boxes.set(item.id, { x: item.x - minX - nodeWidth / 2, y: item.y - minY - halfHeights[i], width: nodeWidth, height: heightFor(item.id) }));
  return { boxes, width: Math.max(900, maxX - minX), height: Math.max(460, maxY - minY) };
});
const showEdgeLabels = computed(() => edges.value.length <= 24);
const selectedEdge = computed(() => edges.value.find((edge) => edge.key === selectedKey.value) || null);
const batchEdges = computed(() => edges.value.filter((edge) => batchKeys.value.has(edge.key) && edge.recommendation));
const stats = computed(() => ({ physical: edges.value.filter((x) => x.kind === "physical").length, inferred: edges.value.filter((x) => x.kind === "inferred").length, confirmed: edges.value.filter((x) => x.kind === "confirmed").length }));

function center(id: string) { const box = layout.value.boxes.get(id) || { x: 0, y: 0, width: nodeWidth, height: 64 }; return { x: box.x + box.width / 2, y: box.y + box.height / 2 }; }
function edgePath(edge: GraphEdge) {
  const a = center(edge.factTableId); const b = center(edge.dimensionTableId);
  const dx = b.x - a.x; const dy = b.y - a.y;
  const bend = Math.max(-90, Math.min(90, dx * 0.18));
  return `M ${a.x} ${a.y} C ${a.x + dx * 0.42} ${a.y + dy * 0.18 - bend}, ${a.x + dx * 0.58} ${a.y + dy * 0.82 + bend}, ${b.x} ${b.y}`;
}
function labelPoint(edge: GraphEdge) { const a = center(edge.factTableId); const b = center(edge.dimensionTableId); return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 - 12 }; }
const focusedNeighbors = computed(() => {
  if (!focusedTableId.value) return null;
  const ids = new Set([focusedTableId.value]);
  for (const edge of edges.value) {
    if (edge.factTableId === focusedTableId.value) ids.add(edge.dimensionTableId);
    if (edge.dimensionTableId === focusedTableId.value) ids.add(edge.factTableId);
  }
  return ids;
});
function toggleFocusTable(tableId: string) {
  focusedTableId.value = focusedTableId.value === tableId ? "" : tableId;
}
function clearFocus() { focusedTableId.value = ""; }
function tableOpacity(tableId: string) {
  return !focusedNeighbors.value || focusedNeighbors.value.has(tableId) ? 1 : 0.22;
}
function edgeOpacity(edge: GraphEdge) {
  if (!focusedTableId.value) return selectedKey.value === edge.key ? 0.95 : 0.72;
  return edge.factTableId === focusedTableId.value || edge.dimensionTableId === focusedTableId.value ? 0.95 : 0.08;
}
function edgeColor(edge: GraphEdge) { return edge.kind === "confirmed" ? "#2563eb" : edge.kind === "physical" ? "#059669" : "#d97706"; }
function tableAccent(index: number) { return ["#2563eb", "#0891b2", "#7c3aed", "#059669", "#d97706", "#dc2626"][index % 6]; }
function displayColumns(tableId: string) { return (tableColumns.value.get(tableId) || []).slice(0, 3); }
function moreCount(tableId: string) { return Math.max(0, (tableColumns.value.get(tableId) || []).length - 3); }
function isPrimaryLike(name: string) { const x = name.toLowerCase(); return x === "id" || x === "f_id" || x.endsWith("_id") === false && x.endsWith("id") && x.length <= 4; }
function evidenceLabel(code: string) { return t(`relation.evidenceLabels.${code}`); }
function selectEdge(edge: GraphEdge) { selectedKey.value = edge.key; }
function toggleBatch(edge: GraphEdge) { if (!edge.recommendation) return; const next = new Set(batchKeys.value); next.has(edge.key) ? next.delete(edge.key) : next.add(edge.key); batchKeys.value = next; }
function clearBatch() { batchKeys.value = new Set(); }
function confirm(edge: GraphEdge) { if (edge.recommendation) emit("confirm", edge.recommendation); }
function confirmBatch() { emit("batchConfirm", batchEdges.value.map((edge) => edge.recommendation!)); clearBatch(); }
function zoomIn() { zoom.value = Math.min(1.8, zoom.value + 0.15); }
function zoomOut() { zoom.value = Math.max(0.35, zoom.value - 0.15); }
function resetView() { zoom.value = 1; panX.value = 0; panY.value = 0; }
function relayout() { layoutVersion.value += 1; resetView(); }
function wheel(event: WheelEvent) { if (!event.ctrlKey && !event.metaKey) return; event.preventDefault(); zoom.value = Math.max(0.55, Math.min(2.2, zoom.value + (event.deltaY < 0 ? 0.08 : -0.08))); }
function startPan(event: MouseEvent) {
  const target = event.target as Element;
  if (target.closest("g.cursor-pointer") || target.closest(".graph-node")) return;
  panning.value = true;
  panStart = { x: event.clientX, y: event.clientY, panX: panX.value, panY: panY.value };
  window.addEventListener("mousemove", movePan);
  window.addEventListener("mouseup", endPan, { once: true });
}
function movePan(event: MouseEvent) {
  if (!panning.value) return;
  panX.value = panStart.panX + (event.clientX - panStart.x) / zoom.value;
  panY.value = panStart.panY + (event.clientY - panStart.y) / zoom.value;
}
function endPan() {
  panning.value = false;
  window.removeEventListener("mousemove", movePan);
}
</script>

<template>
  <div class="flex flex-col gap-4" v-loading="loading ?? false">
    <div class="flex flex-col gap-3 rounded-xl border border-[var(--ep-border-color-lighter)] bg-[var(--ep-bg-color)] px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
      <div class="flex flex-wrap items-center gap-4 text-xs text-[var(--ep-text-color-secondary)]">
        <span class="font-medium text-[var(--ep-text-color-primary)]">{{ t("relation.graphLegend") }}</span>
        <span class="inline-flex items-center gap-1.5"><i class="h-2.5 w-2.5 rounded-full bg-[#059669]"></i>{{ t("relation.graphKind.physical") }} {{ stats.physical }}</span>
        <span class="inline-flex items-center gap-1.5"><i class="h-2.5 w-2.5 rounded-full bg-[#d97706]"></i>{{ t("relation.graphKind.inferred") }} {{ stats.inferred }}</span>
        <span class="inline-flex items-center gap-1.5"><i class="h-2.5 w-2.5 rounded-full bg-[#2563eb]"></i>{{ t("relation.graphKind.confirmed") }} {{ stats.confirmed }}</span>
        <span class="hidden lg:inline">{{ t("relation.focusHint") }}</span>
      </div>
      <div class="flex flex-wrap gap-2">
        <el-button size="small" :icon="ZoomOut" :aria-label="t('relation.zoomOut')" @click="zoomOut" />
        <el-button size="small" :icon="ZoomIn" :aria-label="t('relation.zoomIn')" @click="zoomIn" />
        <el-button size="small" :icon="RotateCcw" @click="resetView">{{ t("relation.resetView") }}</el-button>
        <el-button size="small" type="primary" plain :icon="Sparkles" @click="relayout">{{ t("relation.relayout") }}</el-button>
      </div>
    </div>

    <div class="flex flex-col gap-4 xl:flex-row">
      <div class="graph-canvas relative min-h-[560px] flex-1 overflow-hidden rounded-2xl border border-[var(--ep-border-color-lighter)]" :class="{ 'cursor-grabbing': panning }" @wheel="wheel">
        <div class="absolute left-4 top-4 z-10 flex max-w-[calc(100%-32px)] flex-wrap gap-2">
          <div class="rounded-full border border-[var(--ep-border-color-lighter)] bg-[var(--ep-bg-color)]/90 px-3 py-1 text-xs text-[var(--ep-text-color-secondary)] shadow-sm">
            {{ tables.length }} {{ t("relation.graphTables") }} · {{ Math.round(zoom * 100) }}%
          </div>
          <button v-if="focusedTableId" class="cursor-pointer rounded-full border border-[var(--ep-color-primary-light-5)] bg-[var(--ep-color-primary-light-9)] px-3 py-1 text-xs text-[var(--ep-color-primary)] shadow-sm" @click="clearFocus">
            {{ tableName(focusedTableId) }} ×
          </button>
        </div>
        <div class="h-full w-full overflow-auto">
          <svg width="100%" height="100%" :viewBox="`0 0 ${layout.width} ${layout.height}`" preserveAspectRatio="xMidYMid meet" class="block h-full min-h-[560px] w-full cursor-grab" @mousedown="startPan">
            <g :transform="`translate(${panX} ${panY}) scale(${zoom})`">
              <g v-for="edge in edges" :key="edge.key" class="cursor-pointer" @click="selectEdge(edge)">
                <path :d="edgePath(edge)" fill="none" stroke="transparent" stroke-width="18" />
                <path :d="edgePath(edge)" fill="none" :stroke="edgeColor(edge)" :stroke-width="selectedKey === edge.key ? 3.4 : 1.8" :stroke-dasharray="edge.kind === 'inferred' ? '7 5' : undefined" :opacity="edgeOpacity(edge)" />
                <circle :cx="center(edge.factTableId).x" :cy="center(edge.factTableId).y" r="3.5" :fill="edgeColor(edge)" />
                <g v-if="showEdgeLabels || selectedKey === edge.key" :transform="`translate(${labelPoint(edge).x} ${labelPoint(edge).y})`">
                  <rect x="-58" y="-12" width="116" height="24" rx="12" fill="var(--ep-bg-color)" :stroke="edgeColor(edge)" stroke-width="1" opacity="0.96" />
                  <text text-anchor="middle" dominant-baseline="middle" class="graph-edge-label">{{ edge.factColumn }} → {{ edge.dimensionColumn }}</text>
                </g>
              </g>
              <g v-for="(table, index) in tables" :key="table.id" class="graph-node" :opacity="tableOpacity(table.id)" @click.stop="toggleFocusTable(table.id)">
                <rect :x="layout.boxes.get(table.id)?.x" :y="layout.boxes.get(table.id)?.y" :width="nodeWidth" :height="heightFor(table.id)" rx="12" fill="var(--ep-bg-color)" stroke="var(--ep-border-color)" stroke-width="1.1" filter="drop-shadow(0 8px 18px rgb(15 23 42 / 0.08))" />
                <path :d="`M ${(layout.boxes.get(table.id)?.x || 0) + 12} ${layout.boxes.get(table.id)?.y} h ${nodeWidth - 24} a 12 12 0 0 1 12 12 v 22 h -${nodeWidth} v -22 a 12 12 0 0 1 12 -12 z`" :fill="tableAccent(index)" opacity="0.1" />
                <circle :cx="(layout.boxes.get(table.id)?.x || 0) + 17" :cy="(layout.boxes.get(table.id)?.y || 0) + 18" r="4" :fill="tableAccent(index)" />
                <text :x="(layout.boxes.get(table.id)?.x || 0) + 30" :y="(layout.boxes.get(table.id)?.y || 0) + 22" class="graph-table-title">{{ table.name.length > 19 ? table.name.slice(0, 19) + "…" : table.name }}</text>
                <text :x="(layout.boxes.get(table.id)?.x || 0) + 14" :y="(layout.boxes.get(table.id)?.y || 0) + 43" class="graph-table-subtitle">{{ table.description || t("relation.graphTableNode") }}</text>
                <g v-for="(column, row) in displayColumns(table.id)" :key="column">
                  <text :x="(layout.boxes.get(table.id)?.x || 0) + 14" :y="(layout.boxes.get(table.id)?.y || 0) + 68 + row * 22" class="graph-column">{{ column }}</text>
                  <text v-if="isPrimaryLike(column)" :x="(layout.boxes.get(table.id)?.x || 0) + nodeWidth - 34" :y="(layout.boxes.get(table.id)?.y || 0) + 68 + row * 22" class="graph-key">PK</text>
                </g>
                <text v-if="moreCount(table.id)" :x="(layout.boxes.get(table.id)?.x || 0) + 14" :y="(layout.boxes.get(table.id)?.y || 0) + heightFor(table.id) - 7" class="graph-more">+{{ moreCount(table.id) }} {{ t("relation.moreColumns") }}</text>
              </g>
            </g>
          </svg>
        </div>
        <el-empty v-if="!tables.length && !loading" :description="t('relation.emptyList')" :image-size="90" />
      </div>

      <div class="w-full shrink-0 rounded-2xl border border-[var(--ep-border-color-lighter)] bg-[var(--ep-bg-color)] p-4 shadow-sm xl:w-[350px]">
        <div class="mb-3 flex items-center justify-between">
          <div class="font-semibold">{{ t("relation.inspector") }}</div>
          <el-tag v-if="selectedEdge" :type="selectedEdge.kind === 'physical' ? 'success' : selectedEdge.kind === 'confirmed' ? 'primary' : 'warning'" size="small">{{ t(`relation.graphKind.${selectedEdge.kind}`) }}</el-tag>
        </div>
        <template v-if="selectedEdge">
          <div class="space-y-3 text-sm">
            <div class="rounded-lg bg-[var(--ep-fill-color-light)] p-3">
              <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.factTable") }}</div>
              <div class="mt-1 font-medium">{{ selectedEdge.factTableName }}</div><code class="text-xs">{{ selectedEdge.factColumn }}</code>
            </div>
            <div class="rounded-lg bg-[var(--ep-fill-color-light)] p-3">
              <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.dimensionTable") }}</div>
              <div class="mt-1 font-medium">{{ selectedEdge.dimensionTableName }}</div><code class="text-xs">{{ selectedEdge.dimensionColumn }}</code>
            </div>
            <div>
              <div class="text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.cardinality") }}</div>
              <el-select v-if="selectedEdge.recommendation" v-model="selectedEdge.recommendation.cardinality" size="small" class="mt-1 w-full"><el-option v-for="item in ['MANY_TO_ONE','ONE_TO_ONE','ONE_TO_MANY','MANY_TO_MANY']" :key="item" :label="item" :value="item" /></el-select>
              <el-tag v-else size="small" class="mt-1">{{ selectedEdge.cardinality }}</el-tag>
            </div>
            <div v-if="selectedEdge.confidence" class="flex gap-4 text-xs"><span>{{ t("relation.confidence") }}: {{ selectedEdge.confidence }}</span><span>{{ t("relation.score") }}: {{ selectedEdge.score }}</span></div>
            <div v-if="selectedEdge.evidence?.length" class="flex flex-wrap gap-1"><el-tag v-for="code in selectedEdge.evidence" :key="code" size="small" effect="plain">{{ evidenceLabel(code) }}</el-tag></div>
          </div>
          <div v-if="selectedEdge.recommendation" class="mt-5 flex flex-col gap-2">
            <el-button type="primary" @click="confirm(selectedEdge)">{{ t("relation.confirm") }}</el-button>
            <el-button @click="toggleBatch(selectedEdge)">{{ batchKeys.has(selectedEdge.key) ? t("relation.removeBatch") : t("relation.addBatch") }}</el-button>
          </div>
        </template>
        <el-empty v-else :description="t('relation.selectEdgeTip')" :image-size="86" />
        <div class="mt-5 border-t border-[var(--ep-border-color-lighter)] pt-4">
          <div class="mb-2 text-xs text-[var(--ep-text-color-secondary)]">{{ t("relation.batchTray", { count: batchEdges.length }) }}</div>
          <div class="flex gap-2"><el-button type="primary" size="small" :disabled="!batchEdges.length" @click="confirmBatch">{{ t("relation.batchConfirm", { count: batchEdges.length }) }}</el-button><el-button size="small" :disabled="!batchEdges.length" @click="clearBatch">{{ t("common.reset") }}</el-button></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.graph-canvas {
  background-color: var(--ep-fill-color-extra-light);
  background-image: radial-gradient(circle, var(--ep-border-color) 1px, transparent 1px);
  background-size: 18px 18px;
}
.graph-table-title { fill: var(--ep-text-color-primary); font-size: 13px; font-weight: 700; }
.graph-table-subtitle { fill: var(--ep-text-color-secondary); font-size: 10px; }
.graph-column { fill: var(--ep-text-color-regular); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11px; }
.graph-key { fill: #059669; font-size: 9px; font-weight: 700; }
.graph-more { fill: var(--ep-text-color-secondary); font-size: 10px; }
.graph-edge-label { fill: var(--ep-text-color-regular); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 10px; }
.graph-node { transition: opacity 0.15s ease; }
.graph-node:hover { opacity: 0.88; }
</style>
