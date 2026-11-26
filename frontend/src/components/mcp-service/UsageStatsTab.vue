<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { EChartsCoreOption } from "echarts/core";
import { useI18n } from "vue-i18n";
import { useMediaQuery } from "@vueuse/core";
import { keepPreviousData, useQuery } from "@tanstack/vue-query";
import { RefreshCw as Refresh, TriangleAlert as Warning, X as Close } from "@lucide/vue";
import type { McpInvocationLogVO } from "~/api/mcp-usage";
import { getMcpUsageStats, listMcpInvocationLogs } from "~/api/mcp-usage";
import { mcpUsageKeys } from "~/api/query-keys";
import BaseChart from "~/components/common/BaseChart.vue";
import DataTableShell from "~/components/common/DataTableShell.vue";
import { formatDateTime, useChartColors } from "~/composables";
import { escapeHtml } from "~/utils/html";
import { fillDailyTrendGaps } from "~/utils/usage-stats";

const { t } = useI18n();
const props = defineProps<{ serviceId: string }>();

const selectedLog = ref<McpInvocationLogVO | null>(null);
const logDetailVisible = ref(false);
const isPhoneDrawer = useMediaQuery("(max-width: 440px)");
const logDetailDrawerSize = computed(() => (isPhoneDrawer.value ? "100%" : "440px"));
const chartColors = useChartColors();

const page = ref(1);
const size = ref(10);
const toolFilter = ref("");
const appliedToolFilter = ref("");
const statusFilter = ref<"all" | "true" | "false">("all");
const successFilter = computed<boolean | null>(() =>
  statusFilter.value === "all" ? null : statusFilter.value === "true",
);

const statsQueryKey = computed(() => mcpUsageKeys.stats(props.serviceId, 15));
const { data: stats, isFetching: statsLoading, refetch: refetchStats } = useQuery({
  queryKey: statsQueryKey,
  queryFn: ({ signal }) => getMcpUsageStats(props.serviceId, 15, signal),
});

const logsQueryKey = computed(() =>
  mcpUsageKeys.logs(
    props.serviceId,
    page.value,
    size.value,
    appliedToolFilter.value,
    successFilter.value,
  ),
);
const { data: logsPage, isFetching: logsLoading, refetch: refetchLogs } = useQuery({
  queryKey: logsQueryKey,
  queryFn: ({ signal }) =>
    listMcpInvocationLogs(
      props.serviceId,
      page.value,
      size.value,
      appliedToolFilter.value || undefined,
      successFilter.value ?? undefined,
      signal,
    ),
  placeholderData: keepPreviousData,
});

const logs = computed(() => logsPage.value?.data || []);
const totalLogs = computed(() => logsPage.value?.total ?? 0);

const trendOption = computed<EChartsCoreOption>(() => {
  const colors = chartColors.value;
  const trend = fillDailyTrendGaps(stats.value?.daily_trend || []);
  const successRateLabel = t("mcpService.stats.successRate");

  return {
    animationDuration: 300,
    grid: { left: 8, right: 12, top: 38, bottom: 4, containLabel: true },
    legend: {
      top: 0,
      right: 0,
      textStyle: { color: colors.textSecondary, fontSize: 11 },
      itemWidth: 10,
      itemHeight: 10,
    },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      backgroundColor: colors.background,
      borderColor: colors.border,
      textStyle: { color: colors.textPrimary, fontSize: 12 },
      formatter: (params: any) => {
        const items = Array.isArray(params) ? params : [params];
        const lines = items.map((item) => {
          const suffix = item.seriesType === "line" ? "%" : "";
          const value = item.value == null ? "-" : `${item.value}${suffix}`;
          return `${item.marker}${escapeHtml(String(item.seriesName ?? ""))}: ${value}`;
        });
        return [escapeHtml(items[0]?.axisValue || ""), ...lines].join("<br/>");
      },
    },
    xAxis: {
      type: "category",
      data: trend.map((item) => item.date),
      axisLabel: {
        color: colors.textSecondary,
        formatter: (value: string) => value.slice(5),
      },
      axisLine: { lineStyle: { color: colors.border } },
      axisTick: { show: false },
    },
    yAxis: [
      {
        type: "value",
        minInterval: 1,
        axisLabel: { color: colors.textSecondary },
        splitLine: { lineStyle: { color: colors.border, opacity: 0.55 } },
      },
      {
        type: "value",
        min: 0,
        max: 100,
        axisLabel: { color: colors.textSecondary, formatter: "{value}%" },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: t("mcpService.stats.successCount"),
        type: "bar",
        stack: "calls",
        barMaxWidth: 18,
        data: trend.map((item) => item.success_calls),
        itemStyle: { color: colors.success },
      },
      {
        name: t("mcpService.stats.failedCount"),
        type: "bar",
        stack: "calls",
        barMaxWidth: 18,
        data: trend.map((item) => item.failed_calls),
        itemStyle: { color: colors.danger, borderRadius: [3, 3, 0, 0] },
      },
      {
        name: successRateLabel,
        type: "line",
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 5,
        data: trend.map((item) => item.success_rate),
        lineStyle: { width: 2, color: colors.primary },
        itemStyle: { color: colors.primary },
      },
    ],
  };
});

const topTools = computed(() =>
  [...(stats.value?.tool_distribution || [])]
    .sort((a, b) => b.call_count - a.call_count)
    .slice(0, 8)
    .reverse(),
);

const toolDistributionOption = computed<EChartsCoreOption>(() => {
  const colors = chartColors.value;
  const tools = topTools.value;

  return {
    animationDuration: 300,
    grid: { left: 8, right: 44, top: 10, bottom: 4, containLabel: true },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      backgroundColor: colors.background,
      borderColor: colors.border,
      textStyle: { color: colors.textPrimary, fontSize: 12 },
      formatter: (params: any) => {
        const item = Array.isArray(params) ? params[0] : params;
        const tool = tools[item?.dataIndex];
        if (!tool) return "";
        return [
          `<strong>${escapeHtml(tool.tool_name)}</strong>`,
          `${t("mcpService.stats.callCount")}: ${tool.call_count}`,
          `${t("mcpService.stats.successRate")}: ${tool.success_rate}%`,
        ].join("<br/>");
      },
    },
    xAxis: {
      type: "value",
      minInterval: 1,
      axisLabel: { color: colors.textSecondary },
      splitLine: { lineStyle: { color: colors.border, opacity: 0.55 } },
    },
    yAxis: {
      type: "category",
      data: tools.map((tool) => tool.tool_name),
      axisLabel: {
        color: colors.textPrimary,
        width: 112,
        overflow: "truncate",
        fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
      },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [
      {
        name: t("mcpService.stats.callCount"),
        type: "bar",
        data: tools.map((tool) => tool.call_count),
        barMaxWidth: 16,
        label: {
          show: true,
          position: "right",
          color: colors.textSecondary,
          fontSize: 11,
          formatter: ({ dataIndex }: any) => `${tools[dataIndex]?.percentage ?? 0}%`,
        },
        itemStyle: { color: colors.primary, borderRadius: [0, 5, 5, 0] },
      },
    ],
  };
});

const applyToolFilter = () => {
  const isSameQuery =
    appliedToolFilter.value === toolFilter.value.trim() && page.value === 1;
  appliedToolFilter.value = toolFilter.value.trim();
  page.value = 1;
  return isSameQuery;
};

const handleRefresh = async () => {
  const needsManualRefetch = applyToolFilter();
  await Promise.all([
    refetchStats(),
    needsManualRefetch ? refetchLogs() : Promise.resolve(),
  ]);
};

const handleStatusFilterChange = () => {
  applyToolFilter();
};

const handleFilterChange = () => {
  if (applyToolFilter()) {
    void refetchLogs();
  }
};

const handlePageChange = (newPage: number) => {
  page.value = newPage;
};

const handleSizeChange = (newSize: number) => {
  size.value = newSize;
  page.value = 1;
};

const openLogDetail = (log: McpInvocationLogVO) => {
  selectedLog.value = log;
  logDetailVisible.value = true;
};

watch(
  () => props.serviceId,
  () => {
    page.value = 1;
  },
);
</script>

<template>
  <div class="flex flex-col gap-6">
    <!-- Top Row: Metric Overview Cards -->
    <div v-loading="statsLoading" class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <div class="metric-card">
        <div class="metric-label">{{ t("mcpService.stats.totalCalls") }}</div>
        <div class="metric-value font-semibold">
          {{ stats?.total_calls ?? 0 }}
          <span class="metric-unit">{{ t("mcpService.stats.times") }}</span>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-label">{{ t("mcpService.stats.todayCalls") }}</div>
        <div class="metric-value font-semibold">
          {{ stats?.today_calls ?? 0 }}
          <span class="metric-unit">{{ t("mcpService.stats.times") }}</span>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-label">{{ t("mcpService.stats.successRate") }}</div>
        <div
          class="metric-value font-semibold"
          :class="{
            'text-[var(--ep-color-success)]': (stats?.success_rate ?? 100) >= 95,
            'text-[var(--ep-color-warning)]': (stats?.success_rate ?? 100) >= 90 && (stats?.success_rate ?? 100) < 95,
            'text-[var(--ep-color-danger)]': (stats?.success_rate ?? 100) < 90,
          }"
        >
          {{ stats ? `${stats.success_rate}%` : "-" }}
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-label">{{ t("mcpService.stats.avgDuration") }}</div>
        <div class="metric-value font-semibold">
          {{ stats?.avg_duration_ms ?? 0 }}
          <span class="metric-unit">{{ t("mcpService.stats.ms") }}</span>
        </div>
      </div>
    </div>

    <!-- Middle Row: 15-day trend & Tool distribution -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <!-- 15-Day Trend -->
      <div class="section-card">
        <div class="section-header flex items-center justify-between mb-4">
          <span class="section-title text-[15px] font-semibold text-[var(--ep-text-color-primary)]">
            {{ t("mcpService.stats.trendTitle") }}
          </span>
        </div>
        <div v-if="!stats?.daily_trend?.length" class="py-6">
          <el-empty :description="t('mcpService.stats.emptyStats')" :image-size="60" />
        </div>
        <BaseChart v-else :option="trendOption" height="280px" />
      </div>

      <!-- Tool Distribution -->
      <div class="section-card">
        <div class="section-header flex items-center justify-between mb-4">
          <span class="section-title text-[15px] font-semibold text-[var(--ep-text-color-primary)]">
            {{ t("mcpService.stats.toolDistributionTitle") }}
          </span>
        </div>
        <div v-if="!stats?.tool_distribution?.length" class="py-6">
          <el-empty :description="t('mcpService.stats.emptyStats')" :image-size="60" />
        </div>
        <BaseChart v-else :option="toolDistributionOption" height="280px" />
        <details v-if="stats?.tool_distribution?.length" class="chart-data-details">
          <summary>{{ t("mcpService.stats.chartData") }}</summary>
          <table>
            <thead>
              <tr>
                <th>{{ t("mcpService.stats.toolName") }}</th>
                <th>{{ t("mcpService.stats.callCount") }}</th>
                <th>{{ t("mcpService.stats.successRate") }}</th>
                <th>{{ t("mcpService.stats.share") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="tool in topTools" :key="tool.tool_name">
                <td class="font-mono">{{ tool.tool_name }}</td>
                <td>{{ tool.call_count }}</td>
                <td>{{ tool.success_rate }}%</td>
                <td>{{ tool.percentage }}%</td>
              </tr>
            </tbody>
          </table>
        </details>
      </div>
    </div>
    <!-- Bottom Section: Invocation Logs Table -->
    <div class="section-card flex flex-col gap-4">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div>
          <div class="text-[15px] font-semibold text-[var(--ep-text-color-primary)]">
            {{ t("mcpService.stats.logsTitle") }}
          </div>
          <div class="text-xs text-[var(--ep-text-color-secondary)] mt-0.5">
            {{ t("mcpService.stats.logsSubtitle") }}
          </div>
        </div>

        <!-- Filter bar -->
        <div class="flex items-center gap-3">
          <el-select
            v-model="statusFilter"
            style="width: 120px"
            @change="handleStatusFilterChange"
          >
            <el-option :label="t('mcpService.stats.statusAll')" value="all" />
            <el-option :label="t('mcpService.stats.statusSuccess')" value="true" />
            <el-option :label="t('mcpService.stats.statusFailed')" value="false" />
          </el-select>

          <el-input
            v-model="toolFilter"
            clearable
            style="width: 180px"
            :placeholder="t('mcpService.stats.toolFilterPlaceholder')"
            @keyup.enter="handleFilterChange"
            @clear="handleFilterChange"
          />

          <el-button :icon="Refresh" @click="handleRefresh">
            {{ t("mcpService.stats.refresh") }}
          </el-button>
        </div>
      </div>

      <!-- Data Table Shell -->
      <DataTableShell
        :loading="logsLoading"
        :total="totalLogs"
        :page="page"
        :page-size="size"
        :page-sizes="[10, 20, 50]"
        @page-change="handlePageChange"
        @page-size-change="handleSizeChange"
      >
        <el-table
          :data="logs"
          class="w-full log-table"
          stripe
          row-key="id"
          @row-click="openLogDetail"
        >
          <template #empty>
            <el-empty :description="t('mcpService.stats.emptyLogs')" :image-size="80" />
          </template>

          <el-table-column
            :label="t('mcpService.stats.time')"
            min-width="170"
          >
            <template #default="{ row }">
              <span class="font-mono text-xs whitespace-nowrap text-[var(--ep-text-color-regular)]">
                {{ formatDateTime(row.created_at) }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('mcpService.stats.toolName')"
            min-width="150"
          >
            <template #default="{ row }">
              <span class="font-mono text-xs font-medium whitespace-nowrap text-[var(--ep-text-color-primary)]">
                {{ row.tool_name || "-" }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('mcpService.stats.caller')"
            min-width="130"
          >
            <template #default="{ row }">
              <span class="text-xs text-[var(--ep-text-color-regular)]">
                {{ row.caller_name || row.caller_id || t('mcpService.stats.unknownCaller') }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('mcpService.stats.clientIp')"
            min-width="130"
          >
            <template #default="{ row }">
              <span class="font-mono text-xs text-[var(--ep-text-color-secondary)]">
                {{ row.client_ip || "-" }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('mcpService.stats.duration')"
            min-width="110"
          >
            <template #default="{ row }">
              <span class="font-mono text-xs text-[var(--ep-text-color-regular)]">
                {{ row.duration_ms }} ms
              </span>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('mcpService.stats.status')"
            width="100"
            align="center"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.success ? 'success' : 'danger'"
                effect="light"
              >
                {{ row.success ? t("mcpService.stats.statusSuccess") : t("mcpService.stats.statusFailed") }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('mcpService.stats.errorMessage')"
            min-width="180"
          >
            <template #default="{ row }">
              <div v-if="row.error_message" class="flex items-center gap-1.5">
                <span class="text-xs text-[var(--ep-color-danger)] truncate max-w-[220px]">
                  {{ row.error_message }}
                </span>
                <el-popover
                  placement="top-start"
                  :title="t('mcpService.stats.errorMessage')"
                  width="350"
                  trigger="hover"
                  :content="row.error_message"
                >
                  <template #reference>
                    <el-icon class="text-[var(--ep-color-danger)] cursor-pointer"><Warning /></el-icon>
                  </template>
                </el-popover>
              </div>
              <span v-else class="text-xs text-[var(--ep-text-color-placeholder)]">-</span>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('common.actions')"
            width="90"
            fixed="right"
            align="center"
          >
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                size="small"
                @click.stop="openLogDetail(row)"
              >
                {{ t("common.detail") }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataTableShell>
    </div>


    <el-drawer
      v-model="logDetailVisible"
      :size="logDetailDrawerSize"
      class="log-detail-drawer"
      :with-header="false"
    >
      <div v-if="selectedLog" class="flex flex-col gap-5 h-full">
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0">
            <div class="text-xs text-[var(--ep-text-color-secondary)] mb-1">
              {{ t("mcpService.stats.logDetailTitle") }}
            </div>
            <div class="font-mono text-base font-semibold text-[var(--ep-text-color-primary)] break-all">
              {{ selectedLog.tool_name || selectedLog.method }}
            </div>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <el-tag
              :type="selectedLog.success ? 'success' : 'danger'"
              effect="light"
            >
              {{ selectedLog.success ? t("mcpService.stats.statusSuccess") : t("mcpService.stats.statusFailed") }}
            </el-tag>
            <el-button
              link
              :icon="Close"
              :aria-label="t('common.close')"
              class="!p-1 text-[var(--ep-text-color-secondary)] hover:text-[var(--ep-text-color-primary)]"
              @click="logDetailVisible = false"
            />
          </div>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div class="detail-metric">
            <span>{{ t("mcpService.stats.duration") }}</span>
            <strong>{{ selectedLog.duration_ms }} ms</strong>
          </div>
          <div class="detail-metric">
            <span>{{ t("mcpService.stats.method") }}</span>
            <strong>{{ selectedLog.method || "-" }}</strong>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">{{ t("mcpService.stats.executionInfo") }}</div>
          <dl class="detail-list">
            <div>
              <dt>{{ t("mcpService.stats.time") }}</dt>
              <dd>{{ formatDateTime(selectedLog.created_at) }}</dd>
            </div>
            <div>
              <dt>{{ t("mcpService.stats.caller") }}</dt>
              <dd>{{ selectedLog.caller_name || selectedLog.caller_id || t("mcpService.stats.unknownCaller") }}</dd>
            </div>
            <div>
              <dt>{{ t("mcpService.stats.clientIp") }}</dt>
              <dd>{{ selectedLog.client_ip || "-" }}</dd>
            </div>
            <div>
              <dt>{{ t("mcpService.stats.statDate") }}</dt>
              <dd>{{ selectedLog.stat_date || "-" }}</dd>
            </div>
            <div>
              <dt>{{ t("mcpService.stats.logId") }}</dt>
              <dd class="font-mono break-all">{{ selectedLog.id }}</dd>
            </div>
          </dl>
        </div>

        <div v-if="selectedLog.error_message" class="detail-section error-section">
          <div class="detail-section-title">{{ t("mcpService.stats.errorMessage") }}</div>
          <pre>{{ selectedLog.error_message }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.chart-data-details {
  margin-top: 8px;
  font-size: 12px;
  color: var(--ep-text-color-secondary);
}

.chart-data-details summary {
  cursor: pointer;
  color: var(--ep-color-primary);
  outline-offset: 2px;
}

.chart-data-details table {
  width: 100%;
  margin-top: 8px;
  border-collapse: collapse;
}

.chart-data-details th,
.chart-data-details td {
  padding: 6px 8px;
  border-top: 1px solid var(--ep-border-color-lighter);
  text-align: left;
}

.chart-data-details th {
  color: var(--ep-text-color-primary);
  font-weight: 600;
}

.metric-card {
  padding: 16px 20px;
  background: var(--ep-bg-color);
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-label {
  font-size: 13px;
  color: var(--ep-text-color-secondary);
}

.metric-value {
  font-size: 24px;
  color: var(--ep-text-color-primary);
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.metric-unit {
  font-size: 12px;
  font-weight: normal;
  color: var(--ep-text-color-secondary);
}

.section-card {
  padding: 20px;
  background: var(--ep-bg-color);
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 8px;
}

.log-table :deep(tbody tr) {
  cursor: pointer;
}

.detail-metric {
  padding: 14px;
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 10px;
  background: var(--ep-fill-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-metric span {
  font-size: 12px;
  color: var(--ep-text-color-secondary);
}

.detail-metric strong {
  font-size: 18px;
  color: var(--ep-text-color-primary);
}

.detail-section {
  padding: 16px;
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 12px;
  background: var(--ep-bg-color);
}

.detail-section-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 650;
  color: var(--ep-text-color-primary);
}

.detail-list {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-list div {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 12px;
}

.detail-list dt {
  color: var(--ep-text-color-secondary);
  font-size: 12px;
}

.detail-list dd {
  margin: 0;
  color: var(--ep-text-color-primary);
  font-size: 13px;
}

.error-section {
  border-color: color-mix(in srgb, var(--ep-color-danger) 28%, var(--ep-border-color-lighter));
}

.error-section pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--ep-color-danger);
  font-size: 12px;
  line-height: 1.6;
}

</style>
