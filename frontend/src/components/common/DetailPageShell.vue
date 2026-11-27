<script setup lang="ts">
export interface DetailBreadcrumbItem {
  label: string;
  to?: string;
}

defineProps<{
  breadcrumbs: DetailBreadcrumbItem[];
  title?: string;
  description?: string;
}>();
</script>

<template>
  <header class="detail-shell">
    <div class="detail-shell-main">
      <el-breadcrumb separator="/" class="detail-shell-breadcrumb">
        <el-breadcrumb-item
          v-for="(item, index) in breadcrumbs"
          :key="`${item.label}-${index}`"
          :to="item.to ? { path: item.to } : undefined"
        >
          {{ item.label }}
        </el-breadcrumb-item>
      </el-breadcrumb>

      <div v-if="title || $slots.badges" class="detail-shell-title-row">
        <h1 v-if="title">{{ title }}</h1>
        <slot name="badges" />
      </div>

      <p v-if="description" class="detail-shell-description">{{ description }}</p>
      <div v-if="$slots.meta" class="detail-shell-meta">
        <slot name="meta" />
      </div>
    </div>

    <div v-if="$slots.actions" class="detail-shell-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.detail-shell {
  display: flex;
  flex-shrink: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  border: 1px solid var(--xiaoai-surface-border);
  border-radius: var(--xiaoai-surface-radius);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--ep-color-primary) 7%, transparent), transparent 46%),
    color-mix(in srgb, var(--ep-bg-color) 94%, transparent);
  box-shadow: var(--xiaoai-surface-shadow);
}

.detail-shell-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.detail-shell-breadcrumb {
  max-width: 100%;
  font-size: 12px;
}

.detail-shell-breadcrumb :deep(.el-breadcrumb__inner) {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
  white-space: nowrap;
}

.detail-shell-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-shell-title-row h1 {
  margin: 0;
  color: var(--ep-text-color-primary);
  font-size: 24px;
  font-weight: 720;
  letter-spacing: -0.03em;
  line-height: 1.25;
}

.detail-shell-description {
  margin: 0;
  color: var(--ep-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.detail-shell-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: var(--ep-text-color-secondary);
  font-size: 12px;
}

.detail-shell-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 768px) {
  .detail-shell {
    flex-direction: column;
    padding: 14px;
  }

  .detail-shell-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
