<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { Play, Settings } from "@lucide/vue";
import { useI18n } from "vue-i18n";
import type { McpToolVO } from "~/api/mcp-tool";
import { notifyError } from "~/api/http";
import { updateTool } from "~/api/mcp-tool";
import ExecuteSqlConfigDialog from "./ExecuteSqlConfigDialog.vue";
import ToolTestDialog from "./ToolTestDialog.vue";

const { t } = useI18n();

const props = defineProps<{ tools: McpToolVO[]; serviceId: string }>();
const emit = defineEmits<{ (e: "refresh"): void }>();

const sqlConfigVisible = ref(false);
const testVisible = ref(false);
const testingTool = ref<McpToolVO | null>(null);
const openTest = (tool: McpToolVO) => {
  testingTool.value = tool;
  testVisible.value = true;
};

const handleToggle = async (tool: McpToolVO) => {
  try {
    await updateTool(props.serviceId, tool.id, { tool_type: tool.tool_type, enabled: !tool.enabled });
    tool.enabled = !tool.enabled;
    ElMessage.success(t("common.saveSuccess"));
    emit("refresh");
  } catch (e: any) {
    notifyError(e, t("common.operationFailed"));
  }
};

const handleSqlConfigSaved = () => {
  sqlConfigVisible.value = false;
  emit("refresh");
};

const toolTypeLabel = (status: string) => {
  switch (status) {
    case "SEARCH_METADATA": return t("mcpService.tool.type.SEARCH_METADATA");
    case "GET_TABLE_INFO": return t("mcpService.tool.type.GET_TABLE_INFO");
    case "LIST_TABLES": return t("mcpService.tool.type.LIST_TABLES");
    case "EXECUTE_SQL": return t("mcpService.tool.type.EXECUTE_SQL");
    case "UPDATE_TABLE_INFO": return t("mcpService.tool.type.UPDATE_TABLE_INFO");
    case "UPDATE_COLUMN_INFO": return t("mcpService.tool.type.UPDATE_COLUMN_INFO");
    case "UPSERT_TERM": return t("mcpService.tool.type.UPSERT_TERM");
    default: return status;
  }
};
</script>

<template>
  <div class="prebuilt-section">
    <div
      v-for="tool in tools"
      :key="tool.id"
      class="prebuilt-card flex items-start gap-4 px-[18px] py-4"
    >
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2 mb-1">
          <span class="card-title">{{ toolTypeLabel(tool.tool_type) }}</span>
          <code class="card-name">{{ tool.name }}</code>
        </div>
        <p class="card-desc">{{ tool.description }}</p>

        <div v-if="tool.tool_type === 'EXECUTE_SQL' && tool.config" class="card-meta flex items-center gap-1.5 mt-2 text-xs">
          <span class="meta-text">
            {{ t("mcpService.tool.allowedOps") }}:
            <el-tag
              v-for="op in Object.entries((tool.config as any).sql_policy || {})"
              :key="op[0]"
              v-show="op[1]"
              size="small"
              class="perm-tag"
            >
              {{ op[0].toUpperCase().replace('ALLOW_', '') }}
            </el-tag>
          </span>
          <span class="meta-divider">|</span>
          <span class="meta-text">{{ t("mcpService.tool.maxRows") }}: {{ (tool.config as any).max_rows ?? 1000 }}</span>
          <span class="meta-divider">|</span>
          <span class="meta-text">{{ t("mcpService.tool.timeout") }}: {{ (tool.config as any).timeout ?? 30 }}s</span>
        </div>
      </div>

      <div class="flex items-center gap-2 shrink-0">
        <el-tooltip v-if="tool.tool_type === 'EXECUTE_SQL'" :content="t('common.config')" placement="top">
          <el-button
            link
            class="tool-action-button"
            :aria-label="t('common.config')"
            @click="sqlConfigVisible = true"
          >
            <Settings :size="16" />
          </el-button>
        </el-tooltip>
        <el-tooltip
          :content="tool.enabled ? t('common.test') : t('mcpService.tool.disabledTestHint')"
          placement="top"
        >
          <el-button
            link
            class="tool-action-button"
            :class="{ 'icon-disabled': !tool.enabled }"
            :disabled="!tool.enabled"
            :aria-label="t('common.test')"
            @click="openTest(tool)"
          >
            <Play :size="16" />
          </el-button>
        </el-tooltip>
        <el-switch
          :model-value="tool.enabled"
          @change="handleToggle(tool)"
        />
      </div>
    </div>

    <ExecuteSqlConfigDialog
      v-if="sqlConfigVisible"
      v-model="sqlConfigVisible"
      :service-id="props.serviceId"
      :tool="tools.find(item => item.tool_type === 'EXECUTE_SQL')!"
      @saved="handleSqlConfigSaved"
    />

    <ToolTestDialog
      v-if="testingTool"
      v-model:visible="testVisible"
      :service-id="props.serviceId"
      :tool="testingTool!"
    />
  </div>
</template>

<style scoped>
.prebuilt-section {
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 12px;
  background: var(--ep-bg-color);
  overflow: hidden;
}
.prebuilt-card {
  border-bottom: 1px solid var(--ep-border-color-lighter);
  transition: background 0.15s;
}
.prebuilt-card:last-child { border-bottom: none; }
.prebuilt-card:hover { background: var(--ep-fill-color-lighter); }
.card-title { font-size: 14px; font-weight: 600; color: var(--ep-text-color-primary); }
.card-name {
  font-size: 12px;
  color: var(--ep-text-color-secondary);
  background: var(--ep-fill-color);
  padding: 1px 6px;
  border-radius: 4px;
}
.card-desc { margin-top: 4px; font-size: 13px; color: var(--ep-text-color-secondary); line-height: 1.5; }
.meta-text { display: flex; align-items: center; gap: 4px; color: var(--ep-text-color-secondary); }
.meta-divider { color: var(--ep-border-color); }
.perm-tag {
  --ep-tag-bg-color: var(--ep-color-purple-light-9);
  --ep-tag-text-color: var(--ep-color-purple);
  --ep-tag-border-color: var(--ep-color-purple-light-7);
}
.tool-action-button {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 6px;
  color: var(--ep-text-color-secondary);
  transition: color 0.15s, background-color 0.15s;
}

.tool-action-button :deep(svg) {
  width: 16px;
  height: 16px;
}

.tool-action-button:hover {
  color: var(--ep-color-primary);
  background: var(--ep-color-primary-light-9);
}

.tool-action-button:focus-visible {
  outline: 2px solid var(--ep-color-primary);
  outline-offset: 2px;
}

.tool-action-button.icon-disabled,
.tool-action-button.icon-disabled:hover {
  cursor: not-allowed;
  color: var(--ep-text-color-placeholder);
  background: transparent;
  opacity: 0.45;
}
</style>
