<route lang="yaml">
meta:
  activeMenu: /mcp-services
</route>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { useI18n } from "vue-i18n";
import { Info as InfoFilled } from "@lucide/vue";
import { Power, PowerOff, Rocket } from "@lucide/vue";
import type { McpServiceDiffVO, McpServiceVO } from "~/api/mcp-service";
import {
  disableMcpService,
  enableMcpService,
  getMcpService,
  getMcpServiceDiff,
  publishMcpService,
  updateMcpService,
} from "~/api/mcp-service";
import { notifyError } from "~/api/http";
import { listPrompts } from "~/api/mcp-prompt";
import DiffSummaryList from "~/components/mcp-service/DiffSummaryList.vue";
import McpAccessConfigCard from "~/components/mcp-service/McpAccessConfigCard.vue";
import { formatDateTime } from "~/composables";

const DataScopeTab = defineAsyncComponent(() => import("~/components/mcp-service/DataScopeTab.vue"));
const DebugPublishTab = defineAsyncComponent(() => import("~/components/mcp-service/DebugPublishTab.vue"));
const PromptsTab = defineAsyncComponent(() => import("~/components/mcp-service/PromptsTab.vue"));
const ToolsTab = defineAsyncComponent(() => import("~/components/mcp-service/ToolsTab.vue"));
const UsageStatsTab = defineAsyncComponent(() => import("~/components/mcp-service/UsageStatsTab.vue"));

const { t } = useI18n();
const route = useRoute("/mcp-services/[id]/");

const serviceId = route.params.id;
const loading = ref(false);
const saving = ref(false);
const service = ref<McpServiceVO | null>(null);
const diff = ref<McpServiceDiffVO | null>(null);
const publishing = ref(false);
const statusChanging = ref(false);
const publishDialogVisible = ref(false);
const releaseNote = ref("");

const formData = ref({
  name: "",
  description: "",
});

const activeTab = ref("basic");

const promptCount = ref(0);

const tabs = computed(() => [
  { key: "basic", label: t("mcpService.tab.basic") },
  { key: "scope", label: t("mcpService.tab.dataScope") },
  { key: "tools", label: t("mcpService.tab.tools"), badge: service.value?.tool_count ?? 0 },
  { key: "prompts", label: t("mcpService.tab.prompts"), badge: promptCount.value },
  { key: "version", label: t("mcpService.tab.version") },
  { key: "stats", label: t("mcpService.tab.usageStats") },
]);

const breadcrumbs = computed(() => [
  { label: t("mcpService.title"), to: "/mcp-services" },
  { label: service.value?.name || serviceId },
]);

const statusLabel = (status?: string) => {
  switch (status) {
    case "DRAFT":
      return t("mcpService.status.draft");
    case "PUBLISHED":
      return t("mcpService.status.published");
    case "DISABLED":
      return t("mcpService.status.disabled");
    default:
      return status || "";
  }
};

const isDirty = computed(() =>
  !!service.value &&
  (formData.value.name !== service.value.name ||
    formData.value.description.trim() !== (service.value.description || "")),
);

/** 变更摘要（右上角 popover + 发布弹窗共用） */
const diffSummary = computed(() => {
  const d = diff.value;
  if (!d) return [];
  const items: {
    label: string;
    added?: string[];
    modified?: string[];
    deleted?: string[];
    detail?: string;
  }[] = [];

  if (d.basic_info_changed) {
    items.push({ label: t("mcpService.tab.basic"), detail: t("common.modified") });
  }
  if (d.data_scope_changed) {
    items.push({ label: t("mcpService.tab.dataScope"), detail: t("common.modified") });
  }
  if (d.tools_changed) {
    items.push({
      label: t("mcpService.tab.tools"),
      added: d.added_tools || [],
      modified: d.modified_tools || [],
      deleted: d.deleted_tools || [],
    });
  }
  if (d.prompts_changed) {
    items.push({
      label: t("mcpService.tab.prompts"),
      added: d.added_prompts || [],
      modified: d.modified_prompts || [],
      deleted: d.deleted_prompts || [],
    });
  }
  return items;
});

const loadService = async () => {
  try {
    loading.value = true;
    const data = await getMcpService(serviceId);
    service.value = data;
    formData.value.name = data.name;
    formData.value.description = data.description || "";
  } catch (error) {
    console.error("Failed to load service detail:", error);
    notifyError(error, t("common.loadFailed"));
  } finally {
    loading.value = false;
  }
};

const loadDiff = async () => {
  try {
    diff.value = await getMcpServiceDiff(serviceId);
  } catch (error) {
    console.error("Failed to load service diff:", error);
    notifyError(error, t("common.loadFailed"));
  }
};

const loadPrompts = async () => {
  try {
    const list = await listPrompts(serviceId);
    promptCount.value = list.length;
  } catch (error) {
    console.error("Failed to load prompts count:", error);
    notifyError(error, t("common.loadFailed"));
  }
};

const refreshAll = async () => {
  await Promise.all([loadService(), loadDiff(), loadPrompts()]);
};

// ── 发布 / 停用 / 启用（页面级状态操作）──

const openPublishDialog = () => {
  releaseNote.value = "";
  publishDialogVisible.value = true;
};

const confirmPublish = async () => {
  try {
    publishing.value = true;
    await publishMcpService(serviceId, { release_note: releaseNote.value.trim() });
    ElMessage.success(
      service.value?.status === "DISABLED"
        ? t("mcpService.publishSuccessDisabled")
        : t("mcpService.publishSuccess"),
    );
    publishDialogVisible.value = false;
    await refreshAll();
  } catch (error: any) {
    console.error("Failed to publish:", error);
    notifyError(error, t("common.operationFailed"));
  } finally {
    publishing.value = false;
  }
};

const handleDisable = async () => {
  try {
    await ElMessageBox.confirm(
      t("mcpService.disableConfirmMsg"),
      t("mcpService.disableConfirmTitle"),
      {
        confirmButtonText: t("common.confirm"),
        cancelButtonText: t("common.cancel"),
        type: "warning",
      },
    );
    statusChanging.value = true;
    await disableMcpService(serviceId);
    ElMessage.success(t("mcpService.disableSuccess"));
    await refreshAll();
  } catch (error: any) {
    console.error("Failed to disable:", error);
    notifyError(error, t("common.operationFailed"));
  } finally {
    statusChanging.value = false;
  }
};

const handleEnable = async () => {
  try {
    statusChanging.value = true;
    await enableMcpService(serviceId);
    ElMessage.success(t("mcpService.enableSuccess"));
    await refreshAll();
  } catch (error: any) {
    console.error("Failed to enable:", error);
    notifyError(error, t("common.operationFailed"));
  } finally {
    statusChanging.value = false;
  }
};

onMounted(() => {
  refreshAll();
});

const handleSave = async () => {
  if (!formData.value.name.trim()) {
    ElMessage.warning(t("common.required", { name: t("common.name") }));
    return;
  }
  try {
    saving.value = true;
    await updateMcpService(serviceId, {
      name: formData.value.name.trim(),
      description: formData.value.description.trim() || undefined,
    });
    ElMessage.success(t("common.saveSuccess"));
    await refreshAll();
  } catch (error: any) {
    console.error("Failed to save:", error);
    notifyError(error, t("common.operationFailed"));
  } finally {
    saving.value = false;
  }
};
</script>

<template>
  <div v-loading="loading" class="mcp-detail-page flex flex-col flex-1 min-h-0 gap-6 px-5 py-6 md:px-8 md:py-7 overflow-hidden">
    <DetailPageShell
      :breadcrumbs="breadcrumbs"
      :title="service?.name || serviceId"
      :description="service?.description || undefined"
    >
      <template #badges>

        <span
          v-if="service"
          class="status-dot-badge inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium transition-all"
          :class="{
            'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/50': service.status === 'PUBLISHED',
            'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-400 border border-amber-200 dark:border-amber-800/50': service.status === 'DISABLED',
            'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300 border border-slate-200 dark:border-slate-700': service.status === 'DRAFT',
          }"
        >
          <span
            class="w-1.5 h-1.5 rounded-full"
            :class="{
              'bg-emerald-500 animate-pulse': service.status === 'PUBLISHED',
              'bg-amber-500': service.status === 'DISABLED',
              'bg-slate-400': service.status === 'DRAFT',
            }"
          />
          {{ statusLabel(service.status) }}
        </span>

        <span
          v-if="service?.active_version_number"
          class="text-xs px-2 py-0.5 rounded-md border inline-flex items-center gap-1.5"
          :class="diff?.has_changes
            ? 'font-medium text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-950/35 border-amber-200 dark:border-amber-800/50'
            : 'font-medium text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/35 border-emerald-200 dark:border-emerald-800/50'"
        >
          <span class="font-mono">{{ t("mcpService.activeVersionState", { version: service.active_version_number }) }}</span>
          <span v-if="diff" class="text-[11px] opacity-90">
            · {{ diff.has_changes ? t("mcpService.withUnpublishedChanges") : t("mcpService.noUnpublishedChanges") }}
          </span>
        </span>
      </template>

      <template #meta>
        <span v-if="service?.endpoint_path">
          {{ t("mcpService.endpointPath") }}: <code class="font-mono">{{ service.endpoint_path }}</code>
        </span>
        <span>ID: {{ serviceId }}</span>
      </template>

      <template #actions>
        <!-- DRAFT：首次发布 -->
        <el-button
          v-if="service?.status === 'DRAFT'"
          type="primary"
          :icon="Rocket"
          :loading="publishing"
          @click="openPublishDialog"
        >
          {{ t("mcpService.publish") }}
        </el-button>

        <!-- PUBLISHED：发布变更（有变更时）+ 停用 -->
        <template v-if="service?.status === 'PUBLISHED'">
          <el-popover
            v-if="diff?.has_changes"
            placement="bottom-end"
            :width="300"
            trigger="hover"
          >
            <template #reference>
              <el-button type="primary" :icon="Rocket" :loading="publishing" @click="openPublishDialog">
                {{ t("mcpService.publishChanges") }}
              </el-button>
            </template>
            <DiffSummaryList :items="diffSummary" :limit="5" :title="t('mcpService.hasUnpublishedChanges')" />
          </el-popover>
          <el-button
            type="warning"
            plain
            :icon="PowerOff"
            :loading="statusChanging"
            @click="handleDisable"
          >
            {{ t("mcpService.disable") }}
          </el-button>
        </template>

        <!-- DISABLED：启用 + 发布变更（有变更时） -->
        <template v-if="service?.status === 'DISABLED'">
          <el-button
            type="success"
            :icon="Power"
            :loading="statusChanging"
            @click="handleEnable"
          >
            {{ t("mcpService.enable") }}
          </el-button>
          <el-button
            v-if="diff?.has_changes"
            type="primary"
            plain
            :icon="Rocket"
            :loading="publishing"
            @click="openPublishDialog"
          >
            {{ t("mcpService.publishChanges") }}
          </el-button>
        </template>
      </template>
    </DetailPageShell>

    <SegmentedTabs v-model="activeTab" :tabs="tabs" class="shrink-0" />

    <!-- Tab Content Body -->
    <main class="detail-main min-w-0 flex-1 min-h-0 overflow-y-auto pr-1 pb-2">
      <!-- Full-Width Responsive Dual-Card Grid Layout (1:1 Equal Split) -->
      <div v-if="activeTab === 'basic'" class="grid grid-cols-1 lg:grid-cols-2 gap-6 w-full">
        <!-- Left Card: Basic Settings Form -->
        <section class="panel p-6 flex flex-col justify-between">
          <el-form label-position="top" class="detail-form flex flex-col gap-4">
            <!-- 只读元数据：服务标识 / 服务 ID -->
            <el-form-item :label="t('mcpService.serviceCodeWithFormat')" class="!mb-0">
              <span class="font-mono text-xs text-[var(--ep-text-color-secondary)] select-all">
                {{ service?.code || '-' }}
              </span>
            </el-form-item>

            <el-form-item :label="t('mcpService.serviceId')" class="!mb-0">
              <span class="font-mono text-xs text-[var(--ep-text-color-secondary)] select-all">
                {{ service?.id || '-' }}
              </span>
            </el-form-item>

            <el-form-item :label="t('common.name')" required class="!mb-0">
              <el-input
                v-model="formData.name"
                :placeholder="t('common.placeholder.name')"
                maxlength="64"
                show-word-limit
              />
            </el-form-item>
            <el-form-item :label="t('common.description')" class="!mb-0">
              <el-input
                v-model="formData.description"
                type="textarea"
                :rows="5"
                maxlength="500"
                show-word-limit
                :placeholder="t('common.placeholder.description')"
              />
            </el-form-item>
          </el-form>

          <div class="mt-4 pt-4 border-t border-[var(--ep-border-color-lighter)] flex items-center justify-between">
            <el-button type="primary" :loading="saving" :disabled="!isDirty" @click="handleSave">
              {{ t("common.save") }}
            </el-button>

            <div class="flex items-center gap-3 text-xs text-[var(--ep-text-color-secondary)]">
              <span v-if="isDirty" class="text-[var(--ep-color-warning)] font-medium flex items-center gap-1">
                <el-icon><InfoFilled /></el-icon> {{ t("common.unsavedChanges") }}
              </span>
              <span>{{ t("common.lastModifiedAt", { time: service?.updated_at ? formatDateTime(service.updated_at) : '-' }) }}</span>
            </div>
          </div>
        </section>

        <!-- Right Card: Technical Access & Client Connection Configuration (5 cols out of 12) -->
        <McpAccessConfigCard :service="service" />
      </div>

      <div v-else-if="activeTab === 'scope'" class="scope-panel p-5 shadow-sm">
        <DataScopeTab :service-id="serviceId" @refresh="refreshAll" />
      </div>
      <div v-else-if="activeTab === 'tools'" class="scope-panel p-5 shadow-sm">
        <ToolsTab :service-id="serviceId" @refresh="refreshAll" />
      </div>
      <div v-else-if="activeTab === 'prompts'" class="scope-panel p-5 shadow-sm">
        <PromptsTab :service-id="serviceId" @refresh="refreshAll" />
      </div>
      <div v-else-if="activeTab === 'version'" class="scope-panel p-[20px] shadow-sm">
        <DebugPublishTab
          :service-id="serviceId"
          :service="service"
          @refresh="refreshAll"
        />
      </div>
      <div v-else-if="activeTab === 'stats'" class="scope-panel p-5 shadow-sm">
        <UsageStatsTab :service-id="serviceId" />
      </div>
    </main>

    <!-- Publish Confirmation Dialog -->
    <el-dialog
      v-model="publishDialogVisible"
      :title="service?.status === 'PUBLISHED' ? t('mcpService.publishChangesConfirmTitle') : t('mcpService.publishConfirmTitle')"
      width="520px"
      append-to-body
    >
      <div class="flex flex-col gap-4">
        <p class="text-sm text-[var(--ep-text-color-regular)] m-0">
          {{ service?.status === 'PUBLISHED'
              ? t('mcpService.publishChangesConfirmDesc')
              : service?.status === 'DISABLED'
                ? t('mcpService.publishDisabledConfirmDesc')
                : t('mcpService.publishConfirmDesc') }}
        </p>

        <!-- 将发布内容摘要 -->
        <div
          v-if="diffSummary.length"
          class="rounded-lg p-3 bg-[var(--ep-fill-color-lighter)] border border-[var(--ep-border-color-lighter)] flex flex-col gap-2"
        >
          <div class="text-xs font-semibold text-[var(--ep-text-color-primary)] mb-0.5 border-b border-[var(--ep-border-color-lighter)] pb-1.5">
            {{ t("mcpService.publishSummaryTitle") }}
          </div>
          <DiffSummaryList :items="diffSummary" />
        </div>

        <el-form label-position="top">
          <el-form-item :label="t('mcpService.releaseNote')">
            <el-input
              v-model="releaseNote"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              :placeholder="t('mcpService.releaseNotePlaceholder')"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer flex justify-end gap-3">
          <el-button @click="publishDialogVisible = false">{{ t("common.cancel") }}</el-button>
          <el-button type="primary" :loading="publishing" @click="confirmPublish">
            {{ t("mcpService.confirmPublish") }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-hero {
  background:
    linear-gradient(90deg, rgba(37, 99, 235, 0.07), transparent 42%),
    var(--ep-bg-color);
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.05);
}

:global(html.dark) .detail-hero {
  background:
    linear-gradient(90deg, rgba(37, 99, 235, 0.14), transparent 46%),
    var(--ep-bg-color);
  box-shadow: 0 18px 44px rgba(0, 0, 0, 0.22);
}

.mcp-detail-page {
  width: min(var(--xiaoai-page-max), 100%);
  margin: 0 auto;
}

.panel,
.scope-panel {
  border: 1px solid var(--xiaoai-surface-border);
  border-radius: var(--xiaoai-surface-radius);
  background: var(--ep-bg-color);
  box-shadow: var(--xiaoai-surface-shadow);
}

.panel-heading h2 {
  margin: 0;
  color: var(--ep-text-color-primary);
  font-size: 16px;
  font-weight: 650;
}

.panel-heading span {
  color: var(--ep-text-color-secondary);
  font-size: 12px;
}

.detail-actions :deep(.el-button .el-icon svg) {
  width: 16px;
  height: 16px;
}
</style>
