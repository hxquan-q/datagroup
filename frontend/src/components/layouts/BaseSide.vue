<script lang="ts" setup>
import { useLocalStorage } from "@vueuse/core";
import {
  Database,
  Layers,
  LayoutDashboard,
  PanelLeftClose,
  PanelLeftOpen,
  PlugZap,
} from "@lucide/vue";
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const collapsed = useLocalStorage("xiaoai.sidebar.collapsed", false);
const route = useRoute();

const navItems = computed(() => [
  {
    path: "/",
    label: t("layout.side.overview"),
    icon: LayoutDashboard,
    exact: true,
  },
  {
    path: "/mcp-services",
    label: t("layout.side.mcpServices"),
    icon: PlugZap,
  },
  {
    path: "/subjects",
    label: t("layout.side.subjects"),
    icon: Layers,
  },
  {
    path: "/datasources",
    label: t("layout.side.dataSources"),
    icon: Database,
  },
]);

function isActive(path: string, exact = false) {
  return exact ? route.path === path : route.path.startsWith(path);
}
</script>

<template>
  <aside class="app-sidebar" :class="{ 'is-collapsed': collapsed }">
    <div class="sidebar-inner">
      <div class="sidebar-caption">{{ t("layout.side.workspace") }}</div>

      <nav class="sidebar-nav" :aria-label="t('layout.side.workspace')">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="sidebar-link"
          :class="{ 'is-active': isActive(item.path, item.exact) }"
          :title="collapsed ? item.label : undefined"
          :aria-label="item.label"
        >
          <span class="sidebar-icon">
            <component :is="item.icon" :size="18" />
          </span>
          <span class="sidebar-label">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <button
        type="button"
        class="sidebar-collapse"
        :aria-label="collapsed ? t('layout.side.expand') : t('layout.side.collapse')"
        :title="collapsed ? t('layout.side.expand') : t('layout.side.collapse')"
        @click="collapsed = !collapsed"
      >
        <PanelLeftOpen v-if="collapsed" :size="17" />
        <PanelLeftClose v-else :size="17" />
        <span v-if="!collapsed">{{ t("layout.side.collapse") }}</span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.app-sidebar {
  width: 216px;
  flex: 0 0 auto;
  min-height: 0;
  color: var(--xiaoai-side-text);
  background: var(--xiaoai-side-bg);
  border-right: 1px solid var(--xiaoai-side-border);
  transition: width 180ms ease;
}

.app-sidebar.is-collapsed {
  width: 72px;
}

.sidebar-inner {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px 10px 12px;
}

.sidebar-caption {
  padding: 0 12px 10px;
  color: var(--xiaoai-side-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  white-space: nowrap;
}

.is-collapsed .sidebar-caption {
  padding-inline: 0;
  text-align: center;
  font-size: 0;
}

.is-collapsed .sidebar-caption::after {
  content: "•";
  font-size: 16px;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 12px;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 12px;
  color: var(--xiaoai-side-text);
  text-decoration: none;
  transition: all 160ms ease;
}

.sidebar-link:hover {
  border-color: transparent;
  background: var(--ep-fill-color-light);
  color: var(--ep-text-color-primary);
}

.sidebar-link.is-active {
  border-color: transparent;
  background: var(--ep-color-primary-light-9);
  color: var(--ep-color-primary);
  box-shadow: none;
}

.sidebar-icon {
  width: 28px;
  height: 28px;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 9px;
  color: var(--xiaoai-side-muted);
}

.sidebar-link.is-active .sidebar-icon {
  color: var(--ep-color-primary);
}

.sidebar-label {
  overflow: hidden;
  font-size: 13px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.is-collapsed .sidebar-link {
  justify-content: center;
  padding: 0;
}

.is-collapsed .sidebar-label {
  display: none;
}

.sidebar-collapse {
  margin-top: auto;
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 10px;
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 10px;
  background: var(--ep-bg-color);
  color: var(--ep-text-color-secondary);
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  transition: all 160ms ease;
}

.sidebar-collapse:hover {
  border-color: var(--ep-color-primary-light-7);
  background: var(--ep-color-primary-light-9);
  color: var(--ep-color-primary);
}

.sidebar-collapse:focus-visible,
.sidebar-link:focus-visible {
  outline: 2px solid var(--ep-color-primary-light-5);
  outline-offset: 2px;
}

@media (max-width: 1100px) {
  .app-sidebar {
    width: 72px;
  }

  .sidebar-label,
  .sidebar-caption,
  .sidebar-collapse {
    display: none;
  }

  .sidebar-link {
    justify-content: center;
    padding: 0;
  }
}

@media (max-width: 768px) {
  .app-sidebar,
  .app-sidebar.is-collapsed {
    width: 100%;
    height: 56px;
    flex: 0 0 56px;
    border-right: 0;
    border-bottom: 1px solid var(--xiaoai-side-border);
  }

  .sidebar-inner {
    flex-direction: row;
    align-items: center;
    padding: 8px 10px;
  }

  .sidebar-nav {
    width: 100%;
    flex-direction: row;
    gap: 6px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .sidebar-nav::-webkit-scrollbar {
    display: none;
  }

  .sidebar-link {
    min-width: 44px;
    min-height: 40px;
    justify-content: center;
    padding: 0 10px;
  }

  .sidebar-label,
  .sidebar-caption,
  .sidebar-collapse {
    display: none;
  }
}
</style>
