<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { RotateCcw, Search, Sparkles } from "@lucide/vue";
import type { ElkNode } from "elkjs";
import { Background, Controls, Handle, MiniMap, Position, VueFlow} from "@xyflow/vue";
import "@xyflow/vue/dist/style.css";
import type { TableInfoVO } from "~/api/subject";
import type { TableRelationRecommendationVO, TableRelationVO } from "~/api/table-relation";

type EdgeKind = "confirmed" | "physical" | "inferred";
interface GraphEdge {
  key: string; kind: EdgeKind; factTableId: string; factTableName: string; factColumn: string;
  dimensionTableId: string; dimensionTableName: string; dimensionColumn: string; cardinality: string;
  confidence?: string; score?: number; evidence?: string[]; recommendation?: TableRelationRecommendationVO;
}
interface TableNodeData extends Record<string, unknown> {
  table: TableInfoVO;
  columns: string[];
  sourcePorts: string[];
  targetPorts: string[];
  accent: string;
}
type RelationNode = any;
type RelationFlowEdge = any;

const props = defineProps<{ tables: TableInfoVO[]; relations: TableRelationVO[]; recommendations: TableRelationRecommendationVO[]; loading?: boolean }>();
const emit = defineEmits<{ confirm: [item: TableRelationRecommendationVO]; batchConfirm: [items: TableRelationRecommendationVO[]] }>();
const { t } = useI18n();

const nodeWidth = 224;
const headerHeight = 58;
const rowHeight = 25;
const selectedKey = ref("");
const focusedTableId = ref("");
const focusedColumn = ref("");
const focusedColumnTableId = ref("");
const searchQuery = ref("");
const showAllTables = ref(false);
const batchKeys = ref<Set<string>>(new Set());
const flowNodes = ref<RelationNode[]>([]);
const flowEdges = ref<RelationFlowEdge[]>([]);
const layoutLoading = ref(false);
const layoutNonce = ref(0);
const instance = ref<{ fitView: (options?: Record<string, unknown>) => unknown; setCenter?: (x: number, y: number, options?: Record<string, unknown>) => unknown } | null>(null);
let elkInstance: { layout: (graph: ElkNode) => Promise<ElkNode> } | null = null;
let layoutRun = 0;
let userClearedFocus = false;
async function getElk() {
  if (!elkInstance) {
    const module = await import("elkjs/lib/elk.bundled.js");
    elkInstance = new module.default();
  }
  return elkInstance;
}

function keyOf(x: { factTableId: string; factColumn: string; dimensionTableId: string; dimensionColumn: string }) {
  return `${x.factTableId}|${x.factColumn}|${x.dimensionTableId}|${x.dimensionColumn}`;
}
function tableName(id: string) { return props.tables.find((table) => table.id === id)?.name || id.slice(0, 8); }
const relationEdges = computed<GraphEdge[]>(() => {
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
const visibleEdges = computed(() => relationEdges.value.filter((edge) => {
  if (!searchQuery.value.trim()) return true;
  return tableMatches(edge.factTableId) || tableMatches(edge.dimensionTableId);
}));
const visibleTableIds = computed(() => {
  const ids = new Set<string>();
  if (!searchQuery.value.trim()) {
    props.tables.forEach((table) => ids.add(table.id));
    return ids;
  }
  const directMatches = new Set<string>();
  for (const table of props.tables) {
    if (tableMatches(table.id)) directMatches.add(table.id);
  }
  directMatches.forEach((id) => ids.add(id));
  for (const edge of relationEdges.value) {
    if (directMatches.has(edge.factTableId)) {
      ids.add(edge.factTableId);
      ids.add(edge.dimensionTableId);
    }
    if (directMatches.has(edge.dimensionTableId)) {
      ids.add(edge.dimensionTableId);
      ids.add(edge.factTableId);
    }
  }
  return ids;
});
const visibleTables = computed(() => props.tables.filter((table) => visibleTableIds.value.has(table.id)));
const displayEdges = computed(() => visibleEdges.value.filter((edge) => visibleTableIds.value.has(edge.factTableId) && visibleTableIds.value.has(edge.dimensionTableId)));

const relationColumns = computed(() => {
  const result = new Map<string, string[]>();
  for (const edge of relationEdges.value) {
    for (const [tableId, column] of [[edge.factTableId, edge.factColumn], [edge.dimensionTableId, edge.dimensionColumn]] as const) {
      const list = result.get(tableId) || [];
      if (!list.includes(column)) list.push(column);
      result.set(tableId, list);
    }
  }
  return result;
});
const tableColumns = computed(() => {
  const result = new Map<string, string[]>();
  for (const edge of displayEdges.value) {
    for (const [tableId, column] of [[edge.factTableId, edge.factColumn], [edge.dimensionTableId, edge.dimensionColumn]] as const) {
      const list = result.get(tableId) || [];
      if (!list.includes(column)) list.push(column);
      result.set(tableId, list);
    }
  }
  return result;
});
const selectedEdge = computed(() => relationEdges.value.find((edge) => edge.key === selectedKey.value) || null);
const batchEdges = computed(() => relationEdges.value.filter((edge) => batchKeys.value.has(edge.key) && edge.recommendation));
const visibleNodeCount = computed(() => {
  const focused = focusedNeighbors();
  if (!focused || showAllTables.value) return visibleTables.value.length;
  return visibleTables.value.filter((table) => focused.has(table.id)).length;
});
const stats = computed(() => ({ physical: relationEdges.value.filter((x) => x.kind === "physical").length, inferred: relationEdges.value.filter((x) => x.kind === "inferred").length, confirmed: relationEdges.value.filter((x) => x.kind === "confirmed").length }));

function tableMatches(tableId: string) {
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) return true;
  const table = props.tables.find((item) => item.id === tableId);
  if (!table) return false;
  const columns = relationColumns.value.get(tableId) || [];
  return table.name.toLowerCase().includes(query) || (table.description || "").toLowerCase().includes(query) || columns.some((column) => column.toLowerCase().includes(query));
}
function columnsFor(tableId: string) { return tableColumns.value.get(tableId) || []; }
function heightFor(tableId: string) { return headerHeight + Math.max(1, columnsFor(tableId).length) * rowHeight + 12; }
function encoded(value: string) { return value.replace(/[^a-zA-Z0-9_]/g, (char) => `_x${char.charCodeAt(0).toString(16)}`); }
function portId(type: "source" | "target", tableId: string, column: string) { return `${type}-${tableId}-${encoded(column)}`; }
function nodeData(data: unknown) { return data as TableNodeData; }
function hasPort(data: unknown, column: string, type: "sourcePorts" | "targetPorts") { return (nodeData(data)[type] || []).includes(column); }
function edgeColor(edge: GraphEdge) { return edge.kind === "confirmed" ? "var(--ep-color-primary)" : edge.kind === "physical" ? "var(--ep-color-success)" : "var(--ep-color-warning)"; }
function tableAccent(index: number) { return ["#2563eb", "#0891b2", "#7c3aed", "#059669", "#d97706", "#dc2626"][index % 6]; }
function isPrimaryLike(name: string) { const x = name.toLowerCase(); return x === "id" || x === "f_id" || (x.endsWith("id") && x.length <= 5); }
function evidenceLabel(code: string) { return t(`relation.evidenceLabels.${code}`); }

function highestDegreeTable(edges: GraphEdge[]) {
  const degree = new Map<string, number>();
  for (const edge of edges) {
    degree.set(edge.factTableId, (degree.get(edge.factTableId) || 0) + 100);
    degree.set(edge.dimensionTableId, (degree.get(edge.dimensionTableId) || 0) + 1);
  }
  return [...degree.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || "";
}
function focusedRadialPositions(tables: TableInfoVO[], edges: GraphEdge[], focusId: string) {
  const positions = new Map<string, { x: number; y: number }>();
  const focus = tables.find((table) => table.id === focusId);
  if (!focus) return positions;
  positions.set(focusId, { x: -nodeWidth / 2, y: -heightFor(focusId) / 2 });
  const outgoing = [...new Set(edges.filter((edge) => edge.factTableId === focusId).map((edge) => edge.dimensionTableId))].filter((id) => id !== focusId);
  const incoming = [...new Set(edges.filter((edge) => edge.dimensionTableId === focusId).map((edge) => edge.factTableId))].filter((id) => id !== focusId && !outgoing.includes(id));
  const related = new Set([focusId, ...outgoing, ...incoming]);
  const placeColumn = (ids: string[], direction: 1 | -1) => {
    const perColumn = 7;
    ids.forEach((id, index) => {
      const column = Math.floor(index / perColumn);
      const row = index % perColumn;
      const rows = Math.min(perColumn, ids.length - column * perColumn);
      const x = direction * (390 + column * 285) + (direction < 0 ? -nodeWidth : 0);
      const y = (row - (rows - 1) / 2) * 132 - heightFor(id) / 2;
      positions.set(id, { x, y });
    });
  };
  placeColumn(outgoing, 1);
  placeColumn(incoming, -1);
  const rest = tables.filter((table) => !related.has(table.id));
  rest.forEach((table, index) => {
    const column = index % 6;
    const row = Math.floor(index / 6);
    positions.set(table.id, { x: (column - 2.5) * 260, y: 690 + row * 145 });
  });
  return positions;
}
async function rebuildLayout() {
  const run = ++layoutRun;
  layoutLoading.value = true;
  const tables = visibleTables.value;
  const edges = displayEdges.value;
  if (!tables.length) {
    flowNodes.value = [];
    flowEdges.value = [];
    layoutLoading.value = false;
    return;
  }
  if ((!focusedTableId.value || !visibleTableIds.value.has(focusedTableId.value)) && edges.length && !userClearedFocus && !searchQuery.value.trim()) {
    focusedTableId.value = highestDegreeTable(edges);
  }
  try {
    const graph: ElkNode = {
      id: "root",
      layoutOptions: {
        "elk.algorithm": "force",
        "elk.force.model": "FRUCHTERMAN_REINGOLD",
        "elk.force.iterations": "450",
        "elk.force.repulsion": "1.2",
        "elk.force.temperature": "0.001",
        "elk.spacing.nodeNode": "24",
        "elk.separateConnectedComponents": "true",
        "elk.aspectRatio": "1.25",
      },

      children: tables.map((table) => {
        const columns = columnsFor(table.id);
        const sourcePorts = edges.filter((edge) => edge.factTableId === table.id).map((edge) => edge.factColumn);
        const targetPorts = edges.filter((edge) => edge.dimensionTableId === table.id).map((edge) => edge.dimensionColumn);
        return {
          id: table.id,
          width: nodeWidth,
          height: heightFor(table.id),
          ports: columns.flatMap((column) => [
            ...(sourcePorts.includes(column) ? [{ id: portId("source", table.id, column), properties: { side: "EAST" } }] : []),
            ...(targetPorts.includes(column) ? [{ id: portId("target", table.id, column), properties: { side: "WEST" } }] : []),
          ]),
        };
      }),
      edges: edges.map((edge) => ({
        id: edge.key,
        sources: [portId("source", edge.factTableId, edge.factColumn)],
        targets: [portId("target", edge.dimensionTableId, edge.dimensionColumn)],
      })),
    };
    let positions = focusedTableId.value ? focusedRadialPositions(tables, edges, focusedTableId.value) : new Map<string, { x: number; y: number }>();
    if (!positions.size) {
      const elk = await getElk();
      const result = await elk.layout(graph);
      positions = new Map((result.children || []).map((item) => [item.id, { x: item.x || 0, y: item.y || 0 }]));
    }
    if (run !== layoutRun) return;
    flowNodes.value = tables.map((table, index) => {
      const layoutNode = positions.get(table.id);
      const columns = columnsFor(table.id);
      return {
        id: table.id,
        type: "table",
        position: { x: layoutNode?.x ?? (index % 5) * 290, y: layoutNode?.y ?? Math.floor(index / 5) * 220 },
        width: nodeWidth,
        height: heightFor(table.id),
        draggable: true,
        selectable: true,
        data: {
          table,
          columns,
          sourcePorts: edges.filter((edge) => edge.factTableId === table.id).map((edge) => edge.factColumn),
          targetPorts: edges.filter((edge) => edge.dimensionTableId === table.id).map((edge) => edge.dimensionColumn),
          accent: tableAccent(index),
        },
      };
    });
    flowEdges.value = edges.map((edge) => makeFlowEdge(edge));
    applyVisualState();
    await nextTick();
    centerGraph();
  } catch {
    flowNodes.value = tables.map((table, index) => ({
      id: table.id,
      type: "table",
      position: { x: (index % 5) * 300, y: Math.floor(index / 5) * 220 },
      width: nodeWidth,
      height: heightFor(table.id),
      data: { table, columns: columnsFor(table.id), sourcePorts: [], targetPorts: [], accent: tableAccent(index) },
    }));
    flowEdges.value = edges.map((edge) => makeFlowEdge(edge));
  } finally {
    if (run === layoutRun) layoutLoading.value = false;
  }
}
function makeFlowEdge(edge: GraphEdge): RelationFlowEdge {
  return {
    id: edge.key,
    source: edge.factTableId,
    target: edge.dimensionTableId,
    sourceHandle: portId("source", edge.factTableId, edge.factColumn),
    targetHandle: portId("target", edge.dimensionTableId, edge.dimensionColumn),
    type: "default",
    data: { relation: edge },
    label: relationEdges.value.length <= 24 ? `${edge.factColumn} → ${edge.dimensionColumn}` : undefined,
    labelShowBg: true,
    labelBgPadding: [6, 3],
    labelBgStyle: { fill: "var(--ep-bg-color)", fillOpacity: 0.92 },
    labelStyle: { fill: "var(--ep-text-color-regular)", fontSize: 10, fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace" },
    style: { stroke: edgeColor(edge), strokeWidth: selectedKey.value === edge.key ? 2.8 : 1.35, strokeDasharray: edge.kind === "inferred" ? "7 5" : undefined, opacity: 0.68 },
  };
}
function applyVisualState() {
  const focused = focusedNeighbors();
  flowNodes.value = flowNodes.value.map((node) => ({
    ...node,
    class: focused && !focused.has(node.id) ? "relation-node relation-node-dimmed" : focusedTableId.value === node.id ? "relation-node relation-node-focused" : "relation-node",
    hidden: Boolean(focused && !showAllTables.value && !focused.has(node.id)),
  }));
  flowEdges.value = flowEdges.value.map((edge) => {
    const relation = (edge.data as { relation: GraphEdge }).relation;
    const focusedHit = !focusedTableId.value || relation.factTableId === focusedTableId.value || relation.dimensionTableId === focusedTableId.value;
    const columnHit = !focusedColumn.value
      || (relation.factTableId === focusedColumnTableId.value && relation.factColumn === focusedColumn.value)
      || (relation.dimensionTableId === focusedColumnTableId.value && relation.dimensionColumn === focusedColumn.value);
    const active = focusedHit && columnHit;
    return {
      ...edge,
      label: selectedKey.value === relation.key || relationEdges.value.length <= 24 ? `${relation.factColumn} → ${relation.dimensionColumn}` : undefined,
      class: active ? "relation-edge" : "relation-edge relation-edge-dimmed",
      hidden: Boolean(focusedTableId.value && !showAllTables.value && !focusedHit),
      style: { ...edge.style, strokeWidth: selectedKey.value === relation.key ? 2.8 : active ? 1.45 : 1, opacity: active ? 1 : 0.045 },
    };
  });
}
function focusedNeighbors() {
  if (!focusedTableId.value) return null;
  const ids = new Set([focusedTableId.value]);
  for (const edge of relationEdges.value) {
    if (edge.factTableId === focusedTableId.value) ids.add(edge.dimensionTableId);
    if (edge.dimensionTableId === focusedTableId.value) ids.add(edge.factTableId);
  }
  return ids;
}
function onInit(flow: { fitView: (options?: Record<string, unknown>) => unknown; setCenter?: (x: number, y: number, options?: Record<string, unknown>) => unknown }) { instance.value = flow; }
function onNodeClick(payload: { node?: RelationNode }) {
  const node = payload.node;
  if (!node) return;
  const next = focusedTableId.value === node.id ? "" : node.id;
  focusedTableId.value = next;
  focusedColumn.value = "";
  focusedColumnTableId.value = "";
  userClearedFocus = !next;
  applyVisualState();
}
function onEdgeClick(payload: { edge?: RelationFlowEdge }) {
  const edge = payload.edge;
  const relation = edge?.data && typeof edge.data === "object" ? (edge.data as { relation?: GraphEdge }).relation : undefined;
  if (relation) selectEdge(relation);
}
function onPaneClick() { clearFocus(); }
function clearFocus() { focusedTableId.value = ""; focusedColumn.value = ""; focusedColumnTableId.value = ""; userClearedFocus = true; applyVisualState(); }
function focusColumn(tableId: string, column: string) {
  userClearedFocus = false;
  focusedTableId.value = tableId;
  const sameColumn = focusedColumnTableId.value === tableId && focusedColumn.value === column;
  focusedColumnTableId.value = sameColumn ? "" : tableId;
  focusedColumn.value = sameColumn ? "" : column;
  applyVisualState();
}
function selectEdge(edge: GraphEdge) { selectedKey.value = edge.key; applyVisualState(); }
function toggleBatch(edge: GraphEdge) { if (!edge.recommendation) return; const next = new Set(batchKeys.value); next.has(edge.key) ? next.delete(edge.key) : next.add(edge.key); batchKeys.value = next; }
function clearBatch() { batchKeys.value = new Set(); }
function confirm(edge: GraphEdge) { if (edge.recommendation) emit("confirm", edge.recommendation); }
function confirmBatch() { emit("batchConfirm", batchEdges.value.map((edge) => edge.recommendation!)); clearBatch(); }
function centerGraph() {
  if (!flowNodes.value.length) return;
  const focused = focusedTableId.value ? flowNodes.value.find((node) => node.id === focusedTableId.value) : null;
  if (focused && instance.value?.setCenter) {
    instance.value.setCenter(focused.position.x + nodeWidth / 2, focused.position.y + heightFor(focused.id) / 2, { zoom: 0.68, duration: 300 });
    return;
  }
  const minX = Math.min(...flowNodes.value.map((node) => node.position.x));
  const maxX = Math.max(...flowNodes.value.map((node) => node.position.x + nodeWidth));
  const minY = Math.min(...flowNodes.value.map((node) => node.position.y));
  const maxY = Math.max(...flowNodes.value.map((node) => node.position.y + heightFor(node.id)));
  if (instance.value?.setCenter) {
    instance.value.setCenter((minX + maxX) / 2, (minY + maxY) / 2, { zoom: 0.58, duration: 300 });
  } else {
    instance.value?.fitView({ padding: 0.14, maxZoom: 0.9, duration: 300 });
  }
}
function resetView() { centerGraph(); }
function toggleShowAll() { showAllTables.value = !showAllTables.value; applyVisualState(); }
function relayout() { layoutNonce.value += 1; rebuildLayout(); }

watch([visibleTables, displayEdges, layoutNonce], () => { rebuildLayout(); }, { immediate: true });
watch([searchQuery], () => { selectedKey.value = ""; focusedTableId.value = ""; focusedColumn.value = ""; focusedColumnTableId.value = ""; userClearedFocus = false; });
watch(focusedTableId, () => { rebuildLayout(); });
</script>

<template>
  <div class="flex flex-col gap-4" v-loading="loading ?? false">
    <div class="flex flex-col gap-3 rounded-xl border border-[var(--ep-border-color-lighter)] bg-[var(--ep-bg-color)] px-4 py-3 xl:flex-row xl:items-center xl:justify-between">
      <div class="flex flex-wrap items-center gap-4 text-xs text-[var(--ep-text-color-secondary)]">
        <span class="font-medium text-[var(--ep-text-color-primary)]">{{ t("relation.graphLegend") }}</span>
        <span class="inline-flex items-center gap-1.5"><i class="h-2.5 w-2.5 rounded-full bg-[var(--ep-color-success)]"></i>{{ t("relation.graphKind.physical") }} {{ stats.physical }}</span>
        <span class="inline-flex items-center gap-1.5"><i class="h-2.5 w-2.5 rounded-full bg-[var(--ep-color-warning)]"></i>{{ t("relation.graphKind.inferred") }} {{ stats.inferred }}</span>
        <span class="inline-flex items-center gap-1.5"><i class="h-2.5 w-2.5 rounded-full bg-[var(--ep-color-primary)]"></i>{{ t("relation.graphKind.confirmed") }} {{ stats.confirmed }}</span>
        <span class="hidden 2xl:inline">{{ t("relation.focusHint") }}</span>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <el-input v-model="searchQuery" size="small" clearable :prefix-icon="Search" :placeholder="t('relation.searchTables')" class="w-56" />
        <el-button size="small" @click="toggleShowAll">{{ showAllTables ? t("relation.focusedOnly") : t("relation.showAll") }}</el-button>
        <el-button size="small" :icon="RotateCcw" @click="resetView">{{ t("relation.resetView") }}</el-button>
        <el-button size="small" type="primary" plain :icon="Sparkles" :loading="layoutLoading" @click="relayout">{{ t("relation.relayout") }}</el-button>
      </div>
    </div>

    <div class="flex flex-col gap-4 xl:flex-row">
      <div class="relation-canvas relative min-h-[620px] flex-1 overflow-hidden rounded-2xl border border-[var(--ep-border-color-lighter)]" v-loading="layoutLoading">
        <div class="absolute left-4 top-4 z-10 flex max-w-[calc(100%-32px)] flex-wrap gap-2">
          <div class="rounded-full border border-[var(--ep-border-color-lighter)] bg-[var(--ep-bg-color)]/90 px-3 py-1 text-xs text-[var(--ep-text-color-secondary)] shadow-sm">
            {{ visibleNodeCount }}/{{ tables.length }} {{ t("relation.graphTables") }}
          </div>
          <button v-if="focusedTableId" class="cursor-pointer rounded-full border border-[var(--ep-color-primary-light-5)] bg-[var(--ep-color-primary-light-9)] px-3 py-1 text-xs text-[var(--ep-color-primary)] shadow-sm" @click="clearFocus">
            {{ tableName(focusedTableId) }}<template v-if="focusedColumn"> · {{ focusedColumn }}</template> ×
          </button>
        </div>
        <VueFlow
          v-model:nodes="flowNodes"
          v-model:edges="flowEdges"
          class="relation-flow"
          :min-zoom="0.08"
          :max-zoom="2.5"
          :nodes-draggable="true"
          :nodes-connectable="false"
          :elements-selectable="true"
          :pan-on-drag="true"
          :zoom-on-scroll="true"
          :zoom-on-pinch="true"
          :select-nodes-on-drag="false"
          @init="onInit"
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
          @pane-click="onPaneClick"
        >
          <Background :gap="18" :size="1" />
          <MiniMap pannable zoomable class="relation-minimap" />
          <Controls position="bottom-right" />
          <template #node-table="{ data }">
            <div class="table-node">
              <div class="table-node-header" :style="{ borderLeftColor: nodeData(data).accent }">
                <span class="table-accent-dot" :style="{ backgroundColor: nodeData(data).accent }"></span>
                <div class="min-w-0">
                  <div class="table-name" :title="nodeData(data).table.name">{{ nodeData(data).table.name }}</div>
                  <div class="table-subtitle" :title="nodeData(data).table.description || undefined">{{ nodeData(data).table.description || t("relation.graphTableNode") }}</div>
                </div>
              </div>
              <div v-if="nodeData(data).columns.length" class="table-column-list">
                <div v-for="column in nodeData(data).columns" :key="column" class="table-column-row" :class="{ 'table-column-active': focusedColumn === column && focusedTableId === nodeData(data).table.id }" @click.stop="focusColumn(nodeData(data).table.id, column)">
                  <Handle v-if="hasPort(data, column, 'targetPorts')" :id="portId('target', nodeData(data).table.id, column)" type="target" :position="Position.Left" class="field-handle field-handle-left" />
                  <Handle v-if="hasPort(data, column, 'sourcePorts')" :id="portId('source', nodeData(data).table.id, column)" type="source" :position="Position.Right" class="field-handle field-handle-right" />
                  <span class="column-name" :title="column">{{ column }}</span>
                  <span v-if="isPrimaryLike(column)" class="pk-badge">PK</span>
                </div>
              </div>
              <div v-else class="table-no-columns">{{ t("relation.noJoinColumns") }}</div>
            </div>
          </template>
        </VueFlow>
        <el-empty v-if="!visibleTables.length && !loading" :description="t('relation.emptyList')" :image-size="90" />
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
          <div class="flex gap-2"><el-button type="primary" size="small" :disabled="!batchEdges.length" @click="confirmBatch">{{ t("relation.batchConfirm", { count: batchEdges.length }) }}</el-button><el-button size="small" :disabled="!batchEdges.length" @click="clearBatch">{{ t("common.clear") }}</el-button></div>
        </div>
      </div>
    </div>
  </div>

</template>

<style scoped>
.relation-canvas { background: var(--ep-fill-color-extra-light); }
.relation-flow { min-height: 620px; color: var(--ep-text-color-primary); }
.relation-flow :deep(.vue-flow__node) { transition: transform 240ms ease; }
.table-node {
  width: 224px;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--ep-border-color);
  border-radius: 10px;
  background: var(--ep-bg-color);
  box-shadow: 0 8px 24px rgb(15 23 42 / 0.08);
}
.table-node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 48px;
  padding: 7px 10px;
  border-bottom: 1px solid var(--ep-border-color-lighter);
  border-left: 3px solid transparent;
  background: var(--ep-fill-color-light);
}
.table-accent-dot { width: 8px; height: 8px; flex: 0 0 auto; border-radius: 999px;  }
.table-name { overflow: hidden; color: var(--ep-text-color-primary); font-size: 13px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.table-subtitle { overflow: hidden; color: var(--ep-text-color-secondary); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.table-column-list { position: relative; }
.table-column-row {
  position: relative;
  display: flex;
  align-items: center;
  height: 25px;
  padding: 0 10px;
  border-bottom: 1px solid color-mix(in srgb, var(--ep-border-color-lighter) 58%, transparent);
  color: var(--ep-text-color-regular);
  cursor: pointer;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
}
.table-column-row:hover, .table-column-active { background: var(--ep-color-primary-light-9); color: var(--ep-color-primary); }
.column-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pk-badge { margin-left: auto; color: var(--ep-color-success); font-size: 9px; font-weight: 700; }
.table-no-columns { padding: 8px 10px; color: var(--ep-text-color-secondary); font-size: 10px; }
.field-handle { width: 7px; height: 7px; border: 1px solid var(--ep-bg-color); background: var(--ep-color-primary); opacity: 0.9; }
.field-handle-left { left: -4px; }
.field-handle-right { right: -4px; }
.relation-node-dimmed .table-node { opacity: 0.16; }
.relation-node-focused .table-node { border-color: var(--ep-color-primary); box-shadow: 0 0 0 2px var(--ep-color-primary-light-7), 0 10px 26px rgb(15 23 42 / 0.14); }
.relation-edge-dimmed { opacity: 0.18; }
.relation-minimap { border: 1px solid var(--ep-border-color-lighter); border-radius: 8px; overflow: hidden; }
</style>
