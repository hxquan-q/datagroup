<script setup lang="ts">
import { computed, ref, onMounted, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useMediaQuery } from "@vueuse/core";
import { Close, Refresh, Warning } from "@element-plus/icons-vue";
import type { McpUsageStatsVO, McpInvocationLogVO } from "~/api/mcp-usage";
import { getMcpUsageStats, listMcpInvocationLogs } from "~/api/mcp-usage";
import DataTableShell from "~/components/common/DataTableShell.vue";
import { formatDateTime } from "~/composables";

const { t } = useI18n();
const props = defineProps<{ serviceId: string }>();

const statsLoading = ref(false);
const logsLoading = ref(false);

const stats = ref<McpUsageStatsVO | null>(null);
const logs = ref<McpInvocationLogVO[]>([]);
const selectedLog = ref<McpInvocationLogVO | null>(null);
const logDetailVisible = ref(false);
const isPhoneDrawer = useMediaQuery("(max-width: 440px)");
const logDetailDrawerSize = computed(() => (isPhoneDrawer.value ? "100%" : "440px"));
const totalLogs = ref(0);

const page = ref(1);
const size = ref(10);
const toolFilter = ref("");
const statusFilter = ref<"all" | "true" | "false">("all");

const loadStats = async () => {
  try {
    statsLoading.value = true;
    stats.value = await getMcpUsageStats(props.serviceId, 15);
  } finally {
    statsLoading.value = false;
  }
};

const loadLogs = async () => {
  try {
    logsLoading.value = true;
    const successParam =
      statusFilter.value === "all"
        ? undefined
        : statusFilter.value === "true";
    const res = await listMcpInvocationLogs(
      props.serviceId,
      page.value,
      size.value,
      toolFilter.value.trim(),
      successParam,
    );
    logs.value = res.data;
    totalLogs.value = res.total;
  } finally {
    logsLoading.value = false;
  }
};

const handleRefresh = async () => {
  await Promise.all([loadStats(), loadLogs()]);
};

const handleFilterChange = () => {
  page.value = 1;
  loadLogs();
};

const handlePageChange = (newPage: number) => {
  page.value = newPage;
  loadLogs();
};

const handleSizeChange = (newSize: number) => {
  size.value = newSize;
  page.value = 1;
  loadLogs();
};

const openLogDetail = (log: McpInvocationLogVO) => {
  selectedLog.value = log;
  logDetailVisible.value = true;
};

watch(() => props.serviceId, () => {
  page.value = 1;
  handleRefresh();
});

onMounted(handleRefresh);
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
        <div v-else class="flex flex-col gap-2 max-h-72 overflow-y-auto pr-1">
          <div
            v-for="item in stats.daily_trend"
            :key="item.date"
            class="flex items-center justify-between text-xs py-1.5 border-b border-[var(--ep-border-color-lighter)] last:border-none"
          >
            <span class="font-mono text-[var(--ep-text-color-regular)] w-24">{{ item.date }}</span>
            <div class="flex-1 mx-3">
              <el-progress
                :percentage="item.success_rate"
                :status="item.success_rate >= 95 ? 'success' : item.success_rate >= 90 ? 'warning' : 'exception'"
                :stroke-width="8"
                :show-text="false"
              />
            </div>
            <div class="flex items-center gap-2 text-right">
              <span class="font-medium text-[var(--ep-text-color-primary)]">{{ item.total_calls }} {{ t("mcpService.stats.times") }}</span>
              <span class="text-[var(--ep-text-color-secondary)] text-[11px]">{{ item.avg_duration_ms }} ms</span>
            </div>
          </div>
        </div>
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
        <div v-else class="flex flex-col gap-3 max-h-72 overflow-y-auto pr-1">
          <div
            v-for="tool in stats.tool_distribution"
            :key="tool.tool_name"
            class="flex flex-col gap-1 text-xs"
          >
            <div class="flex items-center justify-between">
              <span class="font-medium font-mono text-[var(--ep-text-color-primary)]">{{ tool.tool_name }}</span>
              <div class="flex items-center gap-2 text-[var(--ep-text-color-secondary)]">
                <span>{{ tool.call_count }} {{ t("mcpService.stats.times") }} ({{ tool.percentage }}%)</span>
                <el-tag
                  size="small"
                  :type="tool.success_rate >= 95 ? 'success' : tool.success_rate >= 90 ? 'warning' : 'danger'"
                >
                  {{ tool.success_rate }}%
                </el-tag>
              </div>
            </div>
            <el-progress :percentage="tool.percentage" :stroke-width="6" :show-text="false" />
          </div>
        </div>
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
            @change="handleFilterChange"
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
