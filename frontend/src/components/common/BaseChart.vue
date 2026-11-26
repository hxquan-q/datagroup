<script setup lang="ts">
import VChart from "vue-echarts";
import { use, type EChartsCoreOption } from "echarts/core";
import { BarChart, LineChart } from "echarts/charts";
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent]);

withDefaults(
  defineProps<{
    option: EChartsCoreOption;
    height?: string;
    loading?: boolean;
  }>(),
  {
    height: "280px",
    loading: false,
  },
);
</script>

<template>
  <VChart
    class="base-chart"
    :style="{ height }"
    :option="option"
    :loading="loading"
    autoresize
  />
</template>

<style scoped>
.base-chart {
  width: 100%;
  min-height: 180px;
}
</style>
