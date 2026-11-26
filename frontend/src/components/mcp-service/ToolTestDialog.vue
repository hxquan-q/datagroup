<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import type { McpToolVO } from "~/api/mcp-tool";
import type { ToolTestResponse } from "~/api/mcp-tool-test";
import { testTool } from "~/api/mcp-tool-test";
import { notifyError } from "~/api/http";
import { getDataScope } from "~/api/mcp-service";
import { resetTablePickerCache } from "~/composables/useTablePicker";

const { t } = useI18n();

const props = defineProps<{
  visible: boolean;
  serviceId: string;
  tool: McpToolVO;
}>();
const emit = defineEmits<{ (e: "update:visible", v: boolean): void }>();

const loading = ref(false);
const response = ref<ToolTestResponse | null>(null);

/** Contract between the dialog shell and tool-specific param forms. */
interface ToolTestParamsExpose {
  getArgs: () => Record<string, any>;
  validate?: () => Promise<boolean>;
}
const paramRef = ref<ToolTestParamsExpose | null>(null);

// Data source options for param forms that pick one
const dataSources = ref<{ id: string; name: string }[]>([]);
// SUBJECT-scope items of this service (UPSERT_TERM subject picker)
const scopeSubjects = ref<{ id: string; name: string }[]>([]);
watch(
  () => props.visible,
  async (v) => {
    if (v) {
      // Fresh state per open: result + table/column option caches
      response.value = null;
      resetTablePickerCache();
      try {
        const resp = await getDataScope(props.serviceId);
        dataSources.value = (resp.resolved_data_sources || []).map(
          (ds: any) => ({
            id: ds.id,
            name: ds.name,
          }),
        );
        scopeSubjects.value = (resp.items || [])
          .filter((s) => s.scope_type === "SUBJECT")
          .map((s) => ({ id: s.reference_id, name: s.reference_name || s.reference_id }));
      } catch {
        /* ignore */
      }
    }
  },
  { immediate: true },
);

const doExecute = async () => {
  loading.value = true;
  response.value = null;
  try {
    const args = paramRef.value?.getArgs() ?? {};
    response.value = await testTool(props.serviceId, props.tool!.id, {
      arguments: args,
    });
  } catch (e: any) {
    notifyError(e, t("common.operationFailed"));
  } finally {
    loading.value = false;
  }
};

const validateParams = async () => {
  try {
    return (await paramRef.value?.validate?.()) ?? true;
  } catch {
    return false;
  }
};

const handleTestClick = async () => {
  if (!(await validateParams())) return;
  await doExecute();
};
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    :title="
      t('mcpService.toolTest.title', { name: tool?.title || tool?.name || '' })
    "
    width="960px"
    :close-on-click-modal="false"
    append-to-body
    destroy-on-close
    class="test-dialog"
  >
    <div class="test-layout flex flex-row h-[520px] relative">
      <!-- ====== Left: Parameter Form ====== -->
      <div
        class="left-panel w-80 shrink-0 flex flex-col pr-5 border-r border-[var(--ep-border-color-lighter)] overflow-y-auto"
      >
        <h4
          class="section-title text-[13px] font-semibold text-[var(--ep-text-color-primary)] mb-3"
        >
          {{ t("mcpService.toolTest.parameters") }}
        </h4>

        <ExecuteSqlParams
          v-if="tool?.tool_type === 'EXECUTE_SQL'"
          ref="paramRef"
          :data-sources="dataSources"
        />
        <ParameterizedSqlParams
          v-else-if="tool?.tool_type === 'PARAMETERIZED_SQL'"
          ref="paramRef"
          :tool="tool"
          :data-sources="dataSources"
        />
        <GetTableInfoParams
          v-else-if="tool?.tool_type === 'GET_TABLE_INFO'"
          ref="paramRef"
          :data-sources="dataSources"
        />
        <UpdateTableInfoParams
          v-else-if="tool?.tool_type === 'UPDATE_TABLE_INFO'"
          ref="paramRef"
          :data-sources="dataSources"
        />
        <UpdateColumnInfoParams
          v-else-if="tool?.tool_type === 'UPDATE_COLUMN_INFO'"
          ref="paramRef"
          :data-sources="dataSources"
        />
        <UpsertTermParams
          v-else-if="tool?.tool_type === 'UPSERT_TERM'"
          ref="paramRef"
          :subjects="scopeSubjects"
        />
        <SearchMetadataParams
          v-else-if="tool?.tool_type === 'SEARCH_METADATA' || tool?.tool_type === 'SEARCH_SEMANTICS'"
          ref="paramRef"
          :require-keywords="tool?.tool_type === 'SEARCH_METADATA'"
        />
        <QueryMetricParams
          v-else-if="tool?.tool_type === 'QUERY_METRIC'"
          ref="paramRef"
        />
        <ListTablesParams
          v-else-if="tool?.tool_type === 'LIST_TABLES'"
          ref="paramRef"
        />

        <!-- Execute button (bottom of left panel) -->
        <div
          class="execute-area mt-auto pt-4 border-t border-[var(--ep-border-color-lighter)]"
        >
          <el-button
            type="primary"
            :loading="loading"
            @click="handleTestClick"
            class="w-full"
          >
            {{ t("mcpService.toolTest.runTest") }}
          </el-button>
        </div>
      </div>

      <!-- ====== Right: Result Area ====== -->
      <ToolTestResult :loading="loading" :response="response" />
    </div>
  </el-dialog>
</template>

<style scoped>
.section-title::before {
  content: "";
  display: inline-block;
  width: 3px;
  height: 13px;
  background-color: var(--ep-color-primary);
  border-radius: 2px;
  margin-right: 8px;
  vertical-align: middle;
  margin-top: -1px;
}

@media (max-width: 768px) {
  .test-layout {
    height: auto !important;
    max-height: calc(100dvh - 150px);
    flex-direction: column !important;
    overflow-y: auto;
  }

  .left-panel {
    width: 100% !important;
    flex: 0 0 auto;
    padding-right: 0 !important;
    padding-bottom: 16px;
    border-right: 0 !important;
    border-bottom: 1px solid var(--ep-border-color-lighter);
    overflow: visible !important;
  }

  .left-panel :deep(.execute-area) {
    position: sticky;
    bottom: 0;
    background: var(--ep-bg-color-overlay);
  }

  :deep(.right-panel) {
    flex: 0 0 auto;
    min-height: 240px;
    padding-left: 0 !important;
    padding-top: 16px;
    overflow: visible !important;
  }
}
</style>
