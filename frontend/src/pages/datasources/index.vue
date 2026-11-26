<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/vue-query";
import type { DatasourceVO } from "~/api/datasource";
import { deleteDataSource, listDataSources } from "~/api/datasource";
import { datasourceKeys } from "~/api/query-keys";
import { Plus, Search, RefreshCw as Refresh } from "@lucide/vue";
import AuthDialog from "~/components/common/AuthDialog.vue";
import { notifyError } from "~/api/http";

const { t } = useI18n();
const router = useRouter();
const queryClient = useQueryClient();

const dialogVisible = ref(false);
const currentDatasource = ref<DatasourceVO | null>(null);
const searchKeyword = ref("");
const appliedKeyword = ref("");
const page = ref(1);
const pageSize = ref(10);

const authDialogVisible = ref(false);
const authResourceId = ref("");

const datasourceQueryKey = computed(() =>
  datasourceKeys.list(page.value, pageSize.value, appliedKeyword.value),
);

const { data: datasourcePage, isFetching } = useQuery({
  queryKey: datasourceQueryKey,
  queryFn: ({ signal }) =>
    listDataSources(
      page.value,
      pageSize.value,
      appliedKeyword.value || undefined,
      signal,
    ),
  placeholderData: keepPreviousData,
});

const loading = computed(() => isFetching.value);
const datasourceList = computed(() => datasourcePage.value?.data || []);
const total = computed(() => datasourcePage.value?.total ?? 0);

const invalidateDatasourceLists = () =>
  queryClient.invalidateQueries({ queryKey: datasourceKeys.lists() });

const handleAuthorize = (datasource: DatasourceVO) => {
  authResourceId.value = datasource.id;
  authDialogVisible.value = true;
};

const handleSearch = () => {
  appliedKeyword.value = searchKeyword.value;
  page.value = 1;
};

const handleClearSearch = () => {
  searchKeyword.value = "";
  appliedKeyword.value = "";
  page.value = 1;
};

const handlePageChange = (p: number) => {
  page.value = p;
};

const handlePageSizeChange = (ps: number) => {
  pageSize.value = ps;
  page.value = 1;
};

const handleRefresh = () => {
  searchKeyword.value = "";
  appliedKeyword.value = "";
  page.value = 1;
  invalidateDatasourceLists();
};

const handleCreate = () => {
  currentDatasource.value = null;
  dialogVisible.value = true;
};

const handleEdit = (datasource: DatasourceVO) => {
  currentDatasource.value = { ...datasource };
  dialogVisible.value = true;
};

const handleTableManage = (datasource: DatasourceVO) => {
  router.push({
    path: `/datasources/${datasource.id}/tables`,
  });
};

const handleDelete = async (datasource: DatasourceVO) => {
  try {
    await ElMessageBox.confirm(
      t("datasource.deleteConfirmMessage", { name: datasource.name }),
      t("common.warning"),
      {
        confirmButtonText: t("common.confirm"),
        cancelButtonText: t("common.cancel"),
        type: "warning",
      },
    );

    await deleteDataSource(datasource.id);
    ElMessage.success(t("common.deleteSuccess"));
    await invalidateDatasourceLists();
  } catch (error) {
    if (error !== "cancel" && error !== "close") {
      console.error("Failed to delete:", error);
      notifyError(error, t("common.operationFailed"));
    }
  }
};

const handleDialogSuccess = () => {
  dialogVisible.value = false;
  invalidateDatasourceLists();
};
</script>

<template>
  <div class="list-page">
    <div class="page-heading">
      <div>
        <h1>{{ t('datasource.title') }}</h1>
        <p>{{ t('datasource.subtitle') }}</p>
      </div>
      <div class="heading-actions">
        <el-button :icon="Refresh" :loading="loading" @click="handleRefresh">
          {{ t("common.refresh") }}
        </el-button>
        <el-button type="primary" :icon="Plus" @click="handleCreate">
          {{ t("datasource.createButton") }}
        </el-button>
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-fields">
        <el-input
          v-model="searchKeyword"
          :placeholder="t('datasource.searchPlaceholder')"
          clearable
          class="toolbar-search"
          @keyup.enter="handleSearch"
          @clear="handleClearSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" plain :icon="Search" @click="handleSearch">
          {{ t("common.search") }}
        </el-button>
      </div>
    </div>

    <DataTableShell
      :loading="loading"
      :total="total"
      :page="page"
      :page-size="pageSize"
      @page-change="handlePageChange"
      @page-size-change="handlePageSizeChange"
    >
      <DatasourceTable
        :data="datasourceList"
        @edit="handleEdit"
        @delete="handleDelete"
        @table-manage="handleTableManage"
        @authorize="handleAuthorize"
      />
    </DataTableShell>

    <DatasourceDialog
      v-model="dialogVisible"
      :datasource="currentDatasource"
      @success="handleDialogSuccess"
    />

    <AuthDialog
      v-model:visible="authDialogVisible"
      resource-type="DATA_SOURCE"
      :resource-id="authResourceId"
    />
  </div>
</template>
