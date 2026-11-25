<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import BaseHeader from "~/components/layouts/BaseHeader.vue";
import BaseSide from "~/components/layouts/BaseSide.vue";
import { useSystemStore } from "~/stores/system";
import { useAuthStore } from "~/stores/auth";

const systemStore = useSystemStore();
const authStore = useAuthStore();
const route = useRoute();
const { t } = useI18n();

const isAuthPage = computed(() => ["/login", "/register"].includes(route.path));
const mainRef = ref<HTMLElement>();

watch(
  () => route.fullPath,
  async () => {
    await nextTick();
    mainRef.value?.scrollTo({ top: 0 });
  },
);

watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      systemStore.loadConfig();
      authStore.fetchUser();
    }
  },
  { immediate: true },
);
</script>

<template>
  <el-config-provider namespace="ep">
    <a href="#main-content" class="skip-link">{{ t('common.skipToContent') }}</a>
    <template v-if="isAuthPage">
      <RouterView />
    </template>
    <template v-else>
      <div class="app-shell">
        <BaseHeader />
        <div class="app-body">
          <BaseSide />
          <main id="main-content" ref="mainRef" tabindex="-1" class="app-main">
            <RouterView />
          </main>
        </div>
      </div>
    </template>
  </el-config-provider>
</template>

<style>
.app-shell {
  min-height: 100vh;
  background: var(--xiaoai-canvas);
}

.app-body {
  display: flex;
  height: calc(100vh - 64px);
  min-height: 0;
  overflow: hidden;
}

.app-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  background:
    radial-gradient(circle at 18% 0%, rgba(37, 99, 235, 0.08), transparent 30%),
    radial-gradient(circle at 90% 8%, rgba(6, 182, 212, 0.08), transparent 28%),
    var(--xiaoai-canvas);
  outline: none;
}

html.dark .app-main {
  background:
    radial-gradient(circle at 18% 0%, rgba(37, 99, 235, 0.16), transparent 34%),
    radial-gradient(circle at 88% 10%, rgba(6, 182, 212, 0.13), transparent 30%),
    var(--xiaoai-canvas);
}
</style>
