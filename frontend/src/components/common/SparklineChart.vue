<script setup lang="ts">
import { computed, ref } from "vue";

const props = withDefaults(defineProps<{
  points: { label: string; value: number }[];
  color: string;
  height?: number;
}>(), {
  height: 42,
});

const width = 200;
const hoverIndex = ref(-1);
const wrapRef = ref<HTMLElement>();

const maxValue = computed(() => Math.max(1, ...props.points.map((p) => p.value)));

function xy(index: number, value: number) {
  const x = props.points.length > 1 ? (index / (props.points.length - 1)) * width : width / 2;
  const y = props.height - 3 - (value / maxValue.value) * (props.height - 8);
  return [x, y] as const;
}

function smoothPath() {
  if (!props.points.length) return "";
  const pts = props.points.map((p, i) => xy(i, p.value));
  if (pts.length === 1) return `M ${pts[0][0]} ${pts[0][1]}`;
  let d = `M ${pts[0][0]} ${pts[0][1]}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[Math.max(0, i - 1)];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[Math.min(pts.length - 1, i + 2)];
    const c1x = p1[0] + (p2[0] - p0[0]) / 6;
    const c1y = p1[1] + (p2[1] - p0[1]) / 6;
    const c2x = p2[0] - (p3[0] - p1[0]) / 6;
    const c2y = p2[1] - (p3[1] - p1[1]) / 6;
    d += ` C ${c1x} ${c1y}, ${c2x} ${c2y}, ${p2[0]} ${p2[1]}`;
  }
  return d;
}

const linePath = computed(smoothPath);
const areaPath = computed(() => {
  if (!linePath.value) return "";
  const last = xy(props.points.length - 1, props.points[props.points.length - 1]?.value ?? 0);
  const first = xy(0, props.points[0]?.value ?? 0);
  return `${linePath.value} L ${last[0]} ${props.height} L ${first[0]} ${props.height} Z`;
});

const hoverPoint = computed(() => {
  if (hoverIndex.value < 0 || hoverIndex.value >= props.points.length) return null;
  const [x, y] = xy(hoverIndex.value, props.points[hoverIndex.value].value);
  return { x, y, point: props.points[hoverIndex.value] };
});

function onMove(event: MouseEvent) {
  const el = wrapRef.value;
  if (!el || !props.points.length) return;
  const rect = el.getBoundingClientRect();
  const ratio = (event.clientX - rect.left) / rect.width;
  hoverIndex.value = Math.max(0, Math.min(props.points.length - 1, Math.round(ratio * (props.points.length - 1))));
}
</script>

<template>
  <div
    ref="wrapRef"
    class="sparkline-chart"
    :style="{ height: `${height}px` }"
    @mousemove="onMove"
    @mouseleave="hoverIndex = -1"
  >
    <svg
      :viewBox="`0 0 ${width} ${height}`"
      preserveAspectRatio="none"
      class="sparkline-svg"
      aria-hidden="true"
    >
      <path v-if="areaPath" :d="areaPath" :fill="color" opacity="0.14" />
      <path v-if="linePath" :d="linePath" fill="none" :stroke="color" stroke-width="2" stroke-linecap="round" vector-effect="non-scaling-stroke" />
      <circle
        v-if="hoverPoint"
        :cx="hoverPoint.x"
        :cy="hoverPoint.y"
        r="3"
        :fill="color"
        stroke="var(--ep-bg-color)"
        stroke-width="1.5"
      />
    </svg>
    <div
      v-if="hoverPoint"
      class="sparkline-tooltip"
      :style="{ left: `${(hoverPoint.x / width) * 100}%` }"
    >
      {{ hoverPoint.point.label }}: {{ hoverPoint.point.value }}
    </div>
  </div>
</template>

<style scoped>
.sparkline-chart {
  position: relative;
  width: 100%;
}

.sparkline-svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.sparkline-tooltip {
  position: absolute;
  bottom: calc(100% + 4px);
  transform: translateX(-50%);
  padding: 3px 8px;
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 6px;
  background: var(--ep-bg-color);
  color: var(--ep-text-color-primary);
  font-size: 11px;
  white-space: nowrap;
  box-shadow: var(--ep-box-shadow-light, 0 2px 8px rgba(0, 0, 0, 0.08));
  pointer-events: none;
  z-index: 2;
}
</style>
