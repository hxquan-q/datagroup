<script lang="ts" setup>
import { isDark, toggleDark } from "~/composables";
import { BookOpen, KeyRound, LogOut, Moon, Sun } from "@lucide/vue";
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { type AppLang, setI18nLanguage } from "~/plugins/i18n";
import { useAuthStore } from "~/stores/auth";

const { t, locale } = useI18n();
const authStore = useAuthStore();
const router = useRouter();

const localeLabel = computed(() => (locale.value === "zh" ? "中文" : "EN"));
const userDisplayName = computed(
  () => authStore.user?.display_name || authStore.user?.name || "",
);
const appVersion = __APP_VERSION__;

async function changeLocale(lang: AppLang) {
  await setI18nLanguage(lang);
}

function handleCommand(command: string) {
  if (command === "logout") {
    authStore.logout();
  } else if (command === "api-keys") {
    router.push("/settings/api-keys");
  }
}

function goToDocs() {
  const target = locale.value === "en" ? "/docs/en/" : "/docs/";
  window.open(target, "_blank");
}
</script>

<template>
  <header class="app-header">
    <RouterLink to="/" class="brand-lockup" aria-label="XIAOAI">
      <span class="brand-mark">
        <img src="/xiaoai.svg" alt="" class="size-7 object-contain" />
      </span>
      <span class="brand-copy">
        <span class="brand-name">{{ t("layout.header.brand") }}</span>
        <span class="brand-tagline">{{ t("home.brandTagline") }}</span>
      </span>
      <span class="version-badge">v{{ appVersion }}</span>
    </RouterLink>

    <div class="header-actions">
      <button
        type="button"
        class="header-icon-button"
        :title="t('layout.header.theme')"
        :aria-label="t('layout.header.theme')"
        @click="toggleDark()"
      >
        <Sun v-if="isDark" :size="17" />
        <Moon v-else :size="17" />
      </button>

      <el-dropdown trigger="click" @command="(cmd: AppLang) => changeLocale(cmd)">
        <button type="button" class="header-text-button" :aria-label="localeLabel">
          {{ localeLabel }}
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="en">English</el-dropdown-item>
            <el-dropdown-item command="zh">中文</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <button
        type="button"
        class="header-icon-button"
        :title="t('layout.header.help')"
        :aria-label="t('layout.header.help')"
        @click="goToDocs"
      >
        <BookOpen :size="17" />
      </button>

      <el-dropdown v-if="authStore.isLoggedIn" trigger="click" @command="handleCommand">
        <button type="button" class="user-trigger">
          <el-avatar :size="28" class="user-avatar">
            {{ userDisplayName.charAt(0).toUpperCase() }}
          </el-avatar>
          <span class="user-name">{{ userDisplayName }}</span>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="api-keys">
              <el-icon><KeyRound :size="15" /></el-icon>
              <span>{{ t("apiKeys.title") }}</span>
            </el-dropdown-item>
            <el-dropdown-item command="logout">
              <el-icon><LogOut :size="15" /></el-icon>
              <span>{{ t("auth.logout") }}</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <RouterLink v-else to="/login" class="login-link">
        <el-button type="primary" size="small">{{ t("auth.login") }}</el-button>
      </RouterLink>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 0 20px 0 18px;
  border-bottom: 1px solid var(--xiaoai-header-border);
  background: color-mix(in srgb, var(--xiaoai-header-bg) 88%, transparent);
  backdrop-filter: blur(18px);
  position: relative;
  z-index: 20;
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: var(--ep-text-color-primary);
  text-decoration: none;
}

.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(145deg, rgba(37, 99, 235, 0.1), rgba(6, 182, 212, 0.08));
  border: 1px solid rgba(37, 99, 235, 0.14);
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.12);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
  min-width: 0;
}

.brand-name {
  font-size: 15px;
  font-weight: 760;
  letter-spacing: 0.02em;
}

.brand-tagline {
  margin-top: 3px;
  max-width: 160px;
  overflow: hidden;
  color: var(--ep-text-color-secondary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-badge {
  margin-left: 2px;
  padding: 3px 6px;
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 999px;
  background: var(--ep-fill-color-light);
  color: var(--ep-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10px;
  line-height: 1;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-icon-button,
.header-text-button,
.user-trigger {
  border: 1px solid transparent;
  background: transparent;
  color: var(--ep-text-color-regular);
  cursor: pointer;
  transition: all 160ms ease;
}

.header-icon-button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 10px;
}

.header-text-button {
  height: 34px;
  padding: 0 10px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
}

.header-icon-button:hover,
.header-text-button:hover,
.user-trigger:hover {
  border-color: var(--ep-border-color-lighter);
  background: var(--ep-fill-color-light);
  color: var(--ep-text-color-primary);
}

.header-icon-button:focus-visible,
.header-text-button:focus-visible,
.user-trigger:focus-visible,
.brand-lockup:focus-visible {
  outline: 2px solid var(--ep-color-primary);
  outline-offset: 2px;
}

.user-trigger {
  height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 5px;
  border-radius: 999px;
}

.user-avatar {
  background: linear-gradient(135deg, var(--xiaoai-blue), var(--xiaoai-cyan));
  color: white;
  font-size: 12px;
  font-weight: 700;
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.login-link {
  text-decoration: none;
}

@media (max-width: 760px) {
  .app-header {
    padding: 0 12px;
  }

  .brand-tagline,
  .version-badge,
  .user-name {
    display: none;
  }
}
</style>
