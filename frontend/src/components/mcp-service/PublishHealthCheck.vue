<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { AlertTriangle, CheckCircle2, CircleDot } from "@lucide/vue";
import { getDataScope } from "~/api/mcp-service";
import { listTools } from "~/api/mcp-tool";
import { listMetrics } from "~/api/metric";
import { listValidationCases } from "~/api/validation-case";
import { listTableRelations } from "~/api/table-relation";
import { notifyError } from "~/api/http";

interface HealthCheckItem {
  key: string;
  status: "pass" | "warning" | "error";
  title: string;
  detail: string;
  tab?: string;
}

const props = defineProps<{ serviceId: string }>();
const emit = defineEmits<{ navigate: [tab: string] }>();
const { t } = useI18n();
const loading = ref(false);
const checks = ref<HealthCheckItem[]>([]);

function normalize(value?: string) {
  return (value || "").trim().toLowerCase();
}

function scopedNames(item: any) {
  const names = new Set<string>();
  for (const value of item.table_names || []) {
    const name = normalize(value);
    if (name) {
      names.add(name);
      names.add(name.split(".").pop() || name);
    }
  }
  for (const table of item.tables || []) {
    const name = normalize(table.name || table.table_name);
    if (name) names.add(name);
  }
  return names;
}

async function loadChecks() {
  loading.value = true;
  try {
    const [scope, tools] = await Promise.all([getDataScope(props.serviceId), listTools(props.serviceId)]);
    const subjectScopes = (scope.items || []).filter((item) => item.scope_type === "SUBJECT");
    const writeTools = tools.prebuilt.filter((tool) => ["UPDATE_TABLE_INFO", "UPDATE_COLUMN_INFO", "UPSERT_TERM"].includes(tool.tool_type) && tool.enabled);
    let validationIssues = 0;
    let staleCases = 0;
    let metricsWithoutTime = 0;
    let relationScopeIssues = 0;
    for (const subjectId of [...new Set(subjectScopes.map((item) => item.reference_id))]) {
      const [metricPage, validationPage, relationPage] = await Promise.all([
        listMetrics(subjectId, 1, 100),
        listValidationCases(subjectId, 1, 100),
        listTableRelations(subjectId, 1, 100),
      ]);
      const metrics = metricPage.data || [];
      validationIssues += (validationPage.data || []).filter((item) => item.enabled !== false && ["FAIL", "ERROR"].includes(item.last_status || "")).length;
      staleCases += (validationPage.data || []).filter((item) => {
        const metric = metrics.find((entry) => entry.id === item.metric_id);
        return item.enabled !== false && item.last_metric_revision && metric?.revision && item.last_metric_revision < metric.revision;
      }).length;
      metricsWithoutTime += metrics.filter((item) => !item.time_field).length;
      const scopeItem = subjectScopes.find((item) => item.reference_id === subjectId);
      if (scopeItem?.access_mode === "IMPORTED_TABLES") {
        const allowed = scopedNames(scopeItem);
        relationScopeIssues += (relationPage.data || []).filter((relation) => {
          const fact = normalize(relation.fact_table_name);
          const dimension = normalize(relation.dimension_table_name);
          return (fact && !allowed.has(fact)) || (dimension && !allowed.has(dimension));
        }).length;
      }
    }
    checks.value = [
      { key: "scope", status: relationScopeIssues ? "warning" : "pass", title: t("mcpService.health.scopeRelations"), detail: relationScopeIssues ? t("mcpService.health.scopeRelationsWarn", { count: relationScopeIssues }) : t("mcpService.health.scopeRelationsPass"), tab: "scope" },
      { key: "time", status: metricsWithoutTime ? "warning" : "pass", title: t("mcpService.health.timeWindows"), detail: metricsWithoutTime ? t("mcpService.health.timeWindowsWarn", { count: metricsWithoutTime }) : t("mcpService.health.timeWindowsPass"), tab: "scope" },
      { key: "validation", status: validationIssues ? "error" : staleCases ? "warning" : "pass", title: t("mcpService.health.validation"), detail: validationIssues ? t("mcpService.health.validationFailed", { count: validationIssues }) : staleCases ? t("mcpService.health.validationStale", { count: staleCases }) : t("mcpService.health.validationPass"), tab: "scope" },
      { key: "write", status: writeTools.length ? "error" : "pass", title: t("mcpService.health.writeTools"), detail: writeTools.length ? t("mcpService.health.writeToolsEnabled", { count: writeTools.length }) : t("mcpService.health.writeToolsPass"), tab: "tools" },
    ];
  } catch (error) {
    notifyError(error, t("common.loadFailed"));
    checks.value = [{ key: "error", status: "warning", title: t("mcpService.health.unavailable"), detail: t("mcpService.health.unavailableDetail") }];
  } finally {
    loading.value = false;
  }
}

function iconFor(status: HealthCheckItem["status"]) {
  if (status === "pass") return CheckCircle2;
  if (status === "warning") return AlertTriangle;
  return CircleDot;
}

onMounted(loadChecks);
</script>

<template>
  <div v-loading="loading" class="rounded-lg border border-[var(--ep-border-color-lighter)] bg-[var(--ep-fill-color-lighter)] p-3">
    <div class="mb-2 border-b border-[var(--ep-border-color-lighter)] pb-1.5 text-xs font-semibold text-[var(--ep-text-color-primary)]">{{ t("mcpService.health.title") }}</div>
    <div class="flex flex-col gap-2">
      <div v-for="check in checks" :key="check.key" class="health-row">
        <component :is="iconFor(check.status)" :size="15" class="health-icon" :class="`health-${check.status}`" />
        <div class="min-w-0 flex-1">
          <div class="text-xs font-semibold text-[var(--ep-text-color-primary)]">{{ check.title }}</div>
          <div class="mt-0.5 text-xs leading-5 text-[var(--ep-text-color-secondary)]">{{ check.detail }}</div>
        </div>
        <el-button v-if="check.tab" type="primary" link size="small" @click="emit('navigate', check.tab)">{{ t("mcpService.health.review") }}</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.health-row { display: flex; align-items: flex-start; gap: 8px; }
.health-icon { margin-top: 2px; flex: 0 0 auto; }
.health-pass { color: var(--ep-color-success); }
.health-warning { color: var(--ep-color-warning); }
.health-error { color: var(--ep-color-danger); }
</style>
