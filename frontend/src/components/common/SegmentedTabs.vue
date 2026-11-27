<script setup lang="ts">
export interface SegmentedTabItem {
  key: string;
  label: string;
  badge?: number | string;
}

defineProps<{
  tabs: SegmentedTabItem[];
}>();

const activeKey = defineModel<string>({ required: true });
</script>

<template>
  <div class="segmented-tabs" role="tablist">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      type="button"
      role="tab"
      :aria-selected="activeKey === tab.key"
      class="segmented-tab"
      :class="{ 'is-active': activeKey === tab.key }"
      @click="activeKey = tab.key"
    >
      <span>{{ tab.label }}</span>
      <span v-if="tab.badge !== undefined" class="segmented-tab-badge">{{ tab.badge }}</span>
    </button>
  </div>
</template>

<style scoped>
.segmented-tabs {
  display: flex;
  width: fit-content;
  max-width: 100%;
  flex-wrap: nowrap;
  gap: 2px;
  overflow-x: auto;
  padding: 4px;
  border-radius: 999px;
  background: var(--ep-fill-color-light);
  scrollbar-width: none;
}

.segmented-tabs::-webkit-scrollbar {
  display: none;
}

.segmented-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  min-height: 32px;
  padding: 0 14px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--ep-text-color-secondary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: color var(--xiaoai-motion), background-color var(--xiaoai-motion);
  white-space: nowrap;
}

.segmented-tab:hover {
  color: var(--ep-text-color-primary);
}

.segmented-tab.is-active {
  background: var(--ep-bg-color);
  color: var(--ep-color-primary);
  font-weight: 650;
}

.segmented-tab:focus-visible {
  outline: 2px solid var(--ep-color-primary-light-5);
  outline-offset: 2px;
}

.segmented-tab-badge {
  min-width: 18px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--ep-fill-color);
  color: var(--ep-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 650;
  line-height: 16px;
  text-align: center;
}

.segmented-tab.is-active .segmented-tab-badge {
  background: var(--ep-color-primary-light-9);
  color: var(--ep-color-primary);
}
</style>
