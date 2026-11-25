<script lang="ts" setup>
import {
  ArrowRight,
  ArrowLeft,
} from "@element-plus/icons-vue";
import { Database, Layers } from "@lucide/vue";
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const collapsed = ref(false);

const route = useRoute();
const activeMenu = computed(() => {
  return (route.meta?.activeMenu as string) || route.path;
});
</script>

<template>
  <div class="relative">
    <el-menu
      router
      :default-active="activeMenu"
      class="el-menu-vertical-demo h-full !border-r-0"
      :collapse="collapsed"
      :collapse-transition="false"
      :class="collapsed ? 'w-16' : 'w-50'"
    >
      <el-menu-item index="/mcp-services">
        <el-icon :size="18">
          <span class="icon-[codicon--mcp] text-[18px]"></span>
        </el-icon>
        <template #title> {{ t("layout.side.mcpServices") }} </template>
      </el-menu-item>
      <el-menu-item index="/subjects">
        <el-icon :size="18">
          <Layers :size="18" />
        </el-icon>
        <template #title> {{ t("layout.side.subjects") }} </template>
      </el-menu-item>
      <el-menu-item index="/datasources">
        <el-icon :size="18">
          <Database :size="18" />
        </el-icon>
        <template #title> {{ t("layout.side.dataSources") }} </template>
      </el-menu-item>
    </el-menu>

    <!-- 展开/收起按钮 -->
    <el-tooltip
      :trigger-keys="[]"
      :content="collapsed ? t('layout.side.expand') : t('layout.side.collapse')"
      placement="right"
    >
      <button
        @click="collapsed = !collapsed"
        :aria-label="collapsed ? t('layout.side.expand') : t('layout.side.collapse')"
        class="absolute top-1/2 left-full -translate-y-full w-3.5 h-12 rounded-r-xl bg-[var(--ep-bg-color)] cursor-pointer hover:bg-[var(--ep-menu-hover-bg-color)] transition-colors duration-300 ease-in-out"
      >
        <el-icon class="w-2 h-3">
          <ArrowRight v-if="collapsed" />
          <ArrowLeft v-else />
        </el-icon>
      </button>
    </el-tooltip>
  </div>
</template>

<style scoped>
:deep(.el-menu) {
  padding: 8px;
  background: transparent;
}

:deep(.el-menu-item) {
  height: 42px;
  margin: 2px 0;
  border-radius: 10px;
  color: var(--ep-text-color-regular);
}

:deep(.el-menu-item:hover) {
  color: var(--ep-text-color-primary);
  background: var(--ep-menu-hover-bg-color);
}

:deep(.el-menu-item.is-active) {
  color: var(--ep-color-primary);
  background: var(--ep-color-primary-light-9);
  font-weight: 600;
}
</style>
