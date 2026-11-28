<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";

const props = withDefaults(defineProps<{
  aliases?: string[];
  ownerName?: string;
  maxVisible?: number;
  editable?: boolean;
}>(), {
  aliases: () => [],
  ownerName: "",
  maxVisible: 3,
  editable: false,
});

const emit = defineEmits<{ (e: "edit"): void }>();
const { t } = useI18n();

const visibleAliases = computed(() => props.aliases.slice(0, props.maxVisible));
const hiddenCount = computed(() => Math.max(props.aliases.length - props.maxVisible, 0));
</script>

<template>
  <div v-if="aliases.length" class="semantic-chip-row">
    <el-tag
      v-for="alias in visibleAliases"
      :key="alias"
      size="small"
      type="info"
      class="semantic-alias-tag"
      :title="alias"
    >
      {{ alias }}
    </el-tag>

    <el-popover v-if="hiddenCount > 0" trigger="click" placement="bottom" :width="300">
      <template #reference>
        <el-button size="small" class="semantic-overflow-button">
          {{ hiddenCount > 0 ? `+${hiddenCount}` : t("common.detail") }}
        </el-button>
      </template>
      <div class="chip-detail-card">
        <div class="chip-detail-header">
          <div>
            <div class="chip-detail-title">{{ t("common.aliases") }}</div>
            <div class="chip-detail-meta">{{ ownerName }} · {{ aliases.length }}</div>
          </div>
          <el-button v-if="editable" link type="primary" size="small" @click="emit('edit')">
            {{ t("common.edit") }}
          </el-button>
        </div>
        <div class="flex flex-wrap gap-1">
          <el-tag
            v-for="alias in aliases"
            :key="alias"
            size="small"
            type="info"
            class="semantic-alias-tag"
          >
            {{ alias }}
          </el-tag>
        </div>
      </div>
    </el-popover>
  </div>
  <span v-else class="text-sm text-[var(--ep-text-color-placeholder)]">-</span>
</template>
