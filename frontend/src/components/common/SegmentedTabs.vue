<script setup lang="ts">
export interface SegmentedTabItem {
  key: string;
  label: string;
  badge?: number | string;
}

const props = defineProps<{
  tabs: SegmentedTabItem[];
}>();

const activeKey = defineModel<string>({ required: true });

function handleKeydown(event: KeyboardEvent, index: number) {
  let nextIndex: number | null = null;

  if (event.key === "ArrowRight") nextIndex = (index + 1) % props.tabs.length;
  if (event.key === "ArrowLeft") nextIndex = (index - 1 + props.tabs.length) % props.tabs.length;
  if (event.key === "Home") nextIndex = 0;
  if (event.key === "End") nextIndex = props.tabs.length - 1;
  if (nextIndex === null || !props.tabs[nextIndex]) return;

  event.preventDefault();
  activeKey.value = props.tabs[nextIndex].key;

  const tablist = (event.currentTarget as HTMLElement).closest('[role="tablist"]');
  tablist?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[nextIndex]?.focus();
}
</script>

<template>
  <div class="segmented-tabs" role="tablist">
    <button
      v-for="(tab, index) in tabs"
      :key="tab.key"
      type="button"
      role="tab"
      :aria-selected="activeKey === tab.key"
      :tabindex="activeKey === tab.key ? 0 : -1"
      class="segmented-tab"
      :class="{ 'is-active': activeKey === tab.key }"
      @click="activeKey = tab.key"
      @keydown="handleKeydown($event, index)"
    >
      <span>{{ tab.label }}</span>
      <span v-if="tab.badge !== undefined" class="segmented-tab-badge">{{ tab.badge }}</span>
    </button>
  </div>
</template>

<style scoped>
.segmented-tabs {
  display: flex;
  width: 100%;
  max-width: 100%;
  flex-wrap: nowrap;
  gap: 2px;
  overflow-x: auto;
  padding: 4px;
  border-radius: 10px;
  background: var(--ep-fill-color);
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
  border-radius: 8px;
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
