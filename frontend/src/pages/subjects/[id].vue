<route lang="yaml">
meta:
  activeMenu: /subjects
</route>

<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { useI18n } from "vue-i18n";
import { Info as InfoFilled } from "@lucide/vue";
import type { SubjectVO, UpdateSubjectRequest } from "~/api/subject";
import { getSubject, updateSubject } from "~/api/subject";
import { notifyError } from "~/api/http";
import { formatDateTime } from "~/composables";
import AuthDialog from "~/components/common/AuthDialog.vue";

const MetricList = defineAsyncComponent(() => import("~/components/subject/MetricList.vue"));
const SubjectTableList = defineAsyncComponent(() => import("~/components/subject/SubjectTableList.vue"));
const TableRelationList = defineAsyncComponent(() => import("~/components/subject/TableRelationList.vue"));
const TermManager = defineAsyncComponent(() => import("~/components/subject/TermManager.vue"));
const ValidationCaseList = defineAsyncComponent(() => import("~/components/subject/ValidationCaseList.vue"));

const { t } = useI18n();
const route = useRoute("/subjects/[id]");

const subjectId = computed(() => route.params.id as string);

const loading = ref(false);
const saving = ref(false);
const authDialogVisible = ref(false);
const subject = ref<SubjectVO | null>(null);

const formData = ref<UpdateSubjectRequest>({});
const newAlias = ref("");
const newAliasInputVisible = ref(false);
const newAliasInputRef = ref();

const activeTab = ref("basic");
const breadcrumbs = computed(() => [
  { label: t("subject.title"), to: "/subjects" },
  { label: subject.value?.name || subjectId.value },
]);

const tabs = computed(() => [
  { key: "basic", label: t("subject.basicInfo") },
  { key: "tables", label: t("subject.tableManagement") },
  { key: "relations", label: t("relation.title") },
  { key: "terms", label: t("subject.termManagement") },
  { key: "metrics", label: t("metric.title") },
  { key: "validation", label: t("subject.validationTab") },
]);

const isDirty = computed(() => {
  if (!subject.value) return false;
  const s = subject.value;
  return (
    formData.value.name !== s.name ||
    (formData.value.description ?? "") !== (s.description ?? "") ||
    JSON.stringify(formData.value.aliases ?? []) !== JSON.stringify(s.aliases ?? [])
  );
});

const loadSubject = async () => {
  try {
    loading.value = true;
    const s = await getSubject(subjectId.value);
    subject.value = s;
    formData.value = {
      name: s.name,
      description: s.description || undefined,
      aliases: s.aliases ? [...s.aliases] : [],
    };
  } catch (error) {
    console.error("Failed to load subject:", error);
    notifyError(error, t("common.loadFailed"));
  } finally {
    loading.value = false;
  }
};

const showNewAliasInput = () => {
  newAliasInputVisible.value = true;
  nextTick(() => {
    newAliasInputRef.value?.focus();
  });
};

const handleNewAliasConfirm = () => {
  const value = newAlias.value.trim();
  if (value && !formData.value.aliases?.includes(value)) {
    if (!formData.value.aliases) {
      formData.value.aliases = [];
    }
    formData.value.aliases.push(value);
  }
  newAlias.value = "";
  newAliasInputVisible.value = false;
};

const removeAlias = (alias: string) => {
  formData.value.aliases = formData.value.aliases?.filter((a) => a !== alias) || [];
};

const handleSave = async () => {
  try {
    saving.value = true;
    await updateSubject(subjectId.value, formData.value);
    ElMessage.success(t("subject.updateSuccess"));
    await loadSubject();
  } catch (error) {
    console.error("Failed to update subject:", error);
    notifyError(error, t("common.operationFailed"));
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  loadSubject();
});
</script>

<template>
  <div v-loading="loading" class="subject-detail-page flex flex-col flex-1 min-h-0 gap-6 p-6 overflow-hidden">
    <!-- Top Navigation Header -->
    <DetailPageShell
      :breadcrumbs="breadcrumbs"
      :title="subject?.name || subjectId"
      :description="subject?.description || undefined"
    >
      <template #meta>
        <span>{{ t("subject.datasource") }}: {{ subject?.datasource_name || subject?.datasource_id || '-' }}</span>
      </template>
      <template #actions>
        <el-button @click="authDialogVisible = true">
          {{ t("permission.button") }}
        </el-button>
      </template>
    </DetailPageShell>

    <!-- Top Horizontal Underline Tabs -->
    <SegmentedTabs v-model="activeTab" :tabs="tabs" />

    <!-- Tab Content Container -->
    <main class="main-content flex-1 min-w-0 min-h-0 overflow-y-auto pr-1 pb-2">
      <!-- Tab 1: Basic Info (Single Panel Container) -->
      <div v-if="activeTab === 'basic'" class="panel p-6 flex flex-col gap-6">
        <!-- Form Fields -->
        <el-form label-position="top" class="subject-form flex flex-col gap-5">
          <!-- Static Readonly ID Field -->
          <el-form-item label="ID" class="!mb-0">
            <span class="font-mono text-xs text-[var(--ep-text-color-secondary)] select-all">{{ subject?.id || '-' }}</span>
          </el-form-item>

          <el-form-item :label="t('common.name')" required class="!mb-0">
            <el-input v-model="formData.name" :placeholder="t('common.placeholder.name')" />
          </el-form-item>

          <el-form-item :label="t('common.aliases')" class="!mb-0">
            <div class="flex gap-2 flex-wrap items-center">
              <el-tag
                v-for="alias in formData.aliases"
                :key="alias"
                closable
                :disable-transitions="false"
                @close="removeAlias(alias)"
              >
                {{ alias }}
              </el-tag>
              <el-input
                v-if="newAliasInputVisible"
                ref="newAliasInputRef"
                v-model="newAlias"
                class="w-28"
                size="small"
                @keyup.enter="handleNewAliasConfirm"
                @blur="handleNewAliasConfirm"
              />
              <el-button v-else size="small" class="!px-2.5" @click="showNewAliasInput">
                + {{ t('common.addAlias') }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item :label="t('common.description')" class="!mb-0">
            <el-input
              v-model="formData.description"
              type="textarea"
              :rows="3"
              :placeholder="t('common.placeholder.description')"
            />
          </el-form-item>

          <!-- Static Readonly Datasource Field -->
          <el-form-item :label="t('subject.datasource')" class="!mb-0">
            <span class="font-mono text-xs text-[var(--ep-text-color-secondary)] select-all">
              {{ subject?.datasource_name || subject?.datasource_id || '-' }}
            </span>
          </el-form-item>
        </el-form>

        <!-- Panel Footer Action Bar -->
        <div class="flex items-center justify-between border-t border-[var(--ep-border-color-lighter)] pt-4">
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!isDirty"
            @click="handleSave"
          >
            {{ t("common.save") }}
          </el-button>

          <div class="flex items-center gap-3 text-xs text-[var(--ep-text-color-secondary)]">
            <span v-if="isDirty" class="text-[var(--ep-color-warning)] font-medium flex items-center gap-1">
              <el-icon><InfoFilled /></el-icon> {{ t("common.unsavedChanges") }}
            </span>
            <span>{{ t("common.lastModifiedAt", { time: subject?.updated_at ? formatDateTime(subject.updated_at) : '-' }) }}</span>
          </div>
        </div>
      </div>

      <!-- Tab 2: Table Management -->
      <div v-else-if="activeTab === 'tables'" class="scope-panel">
        <SubjectTableList
          v-if="subject?.datasource_id"
          :subject-id="subjectId"
          :datasource-id="subject.datasource_id"
          @refresh="loadSubject"
        />
      </div>

      <div v-else-if="activeTab === 'relations'" class="scope-panel">
        <TableRelationList v-if="subject?.datasource_id" :subject-id="subjectId" :datasource-id="subject.datasource_id" />
      </div>

      <!-- Tab 3: Term Management -->
      <div v-else-if="activeTab === 'terms'" class="scope-panel">
        <TermManager :subject-id="subjectId" />
      </div>

      <!-- Tab 4: Governed Metrics -->
      <div v-else-if="activeTab === 'metrics'" class="scope-panel">
        <MetricList :subject-id="subjectId" />
      </div>

      <!-- Tab 5: Validation Examples -->
      <div v-else-if="activeTab === 'validation'" class="scope-panel validation-panel">
        <ValidationCaseList :subject-id="subjectId" />
      </div>
    </main>

    <AuthDialog
      v-model:visible="authDialogVisible"
      resource-type="SUBJECT"
      :resource-id="subjectId"
    />
  </div>
</template>

<style scoped>
.subject-detail-page {
  width: 100%;
  max-width: var(--xiaoai-page-max);
  margin: 0 auto;
}

.panel,
.scope-panel {
  padding: 18px;
  border: 1px solid var(--xiaoai-surface-border);
  border-radius: var(--xiaoai-surface-radius);
  background: var(--ep-bg-color);
  box-shadow: var(--xiaoai-surface-shadow);
}

.panel {
  padding: 24px;
}
.validation-panel {
  min-height: 320px;
}

@media (max-width: 1024px) {
  .subject-detail-page {
    padding: 16px;
    gap: 14px;
  }

}

@media (max-width: 640px) {
  .subject-detail-page {
    padding: 12px;
  }

  .scope-panel {
    padding: 14px;
  }
}
</style>
