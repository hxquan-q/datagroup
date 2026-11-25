<script lang="ts" setup>
import { useLocalStorage } from "@vueuse/core";
import {
  Database,
  Layers,
  LayoutDashboard,
  PanelLeftClose,
  PanelLeftOpen,
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
    icon: "mcp",
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
        >
          <span class="sidebar-icon">
            <span v-if="item.icon === 'mcp'" class="icon-[codicon--mcp] text-[18px]"></span>
            <component v-else :is="item.icon" :size="18" />
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
  width: 236px;
  flex: 0 0 auto;
  min-height: 0;
  color: rgba(226, 232, 240, 0.86);
  background:
    radial-gradient(circle at 20% 0%, rgba(37, 99, 235, 0.2), transparent 32%),
    linear-gradient(180deg, var(--xiaoai-sidebar-start) 0%, var(--xiaoai-sidebar-end) 100%);
  border-right: 1px solid rgba(148, 163, 184, 0.12);
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
  color: rgba(148, 163, 184, 0.72);
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
  color: rgba(203, 213, 225, 0.8);
  text-decoration: none;
  transition: all 160ms ease;
}

.sidebar-link:hover {
  border-color: rgba(148, 163, 184, 0.12);
  background: rgba(148, 163, 184, 0.08);
  color: var(--xiaoai-ink-strong);
}

.sidebar-link.is-active {
  border-color: rgba(96, 165, 250, 0.22);
  background: linear-gradient(90deg, rgba(37, 99, 235, 0.28), rgba(6, 182, 212, 0.12));
  color: var(--xiaoai-ink-strong);
  box-shadow: inset 3px 0 0 rgba(34, 211, 238, 0.9), 0 10px 24px rgba(2, 6, 23, 0.16);
}

.sidebar-icon {
  width: 28px;
  height: 28px;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 9px;
  color: rgba(165, 180, 252, 0.88);
}

.sidebar-link.is-active .sidebar-icon {
  color: var(--xiaoai-cyan-soft);
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
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 11px;
  background: rgba(15, 23, 42, 0.38);
  color: rgba(203, 213, 225, 0.72);
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  transition: all 160ms ease;
}

.sidebar-collapse:hover {
  border-color: rgba(96, 165, 250, 0.24);
  background: rgba(37, 99, 235, 0.16);
  color: var(--xiaoai-ink-strong);
}

.sidebar-collapse:focus-visible,
.sidebar-link:focus-visible {
  outline: 2px solid var(--xiaoai-cyan-bright);
  outline-offset: 2px;
}

@media (max-width: 768px) {
  .app-sidebar {
    width: 72px;
  }

  .sidebar-label,
  .sidebar-caption,
  .sidebar-collapse span {
    display: none;
  }

  .sidebar-link {
    justify-content: center;
    padding: 0;
  }
}
</style>
