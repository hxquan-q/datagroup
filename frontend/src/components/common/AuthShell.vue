<script setup lang="ts">
import { Cable, Database, ShieldCheck, Sparkles } from "@lucide/vue";
import { useI18n } from "vue-i18n";

const { t } = useI18n();

const highlights = [
  { icon: Database, key: "home.workflow.step1Title" },
  { icon: ShieldCheck, key: "home.features.security" },
  { icon: Sparkles, key: "home.features.semanticPrecision" },
  { icon: Cable, key: "home.features.flexibleIntegration" },
];
</script>

<template>
  <div class="auth-page">
    <section class="auth-panel">
      <div class="auth-brand">
        <div class="auth-logo" aria-label="XIAOAI">
          <img src="/xiaoai.svg" alt="" />
          <span>XIAOAI</span>
        </div>
        <h1>{{ t("home.heroTitle") }}</h1>
        <p>{{ t("home.heroDesc") }}</p>
        <div class="auth-highlights">
          <div v-for="item in highlights" :key="item.key" class="auth-highlight">
            <component :is="item.icon" :size="17" />
            <span>{{ t(item.key) }}</span>
          </div>
        </div>
      </div>

      <div class="auth-card">
        <slot />
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 20px;
  background:
    radial-gradient(circle at 15% 8%, rgba(37, 99, 235, 0.14), transparent 32%),
    radial-gradient(circle at 88% 18%, rgba(6, 182, 212, 0.14), transparent 30%),
    var(--xiaoai-canvas);
}

.auth-panel {
  width: min(1020px, 100%);
  min-height: 620px;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) 430px;
  overflow: hidden;
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 28px;
  background: color-mix(in srgb, var(--ep-bg-color) 92%, transparent);
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.11);
}

.auth-brand {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 56px;
  color: var(--xiaoai-ink-strong);
  background:
    radial-gradient(circle at 20% 0%, rgba(37, 99, 235, 0.26), transparent 36%),
    linear-gradient(145deg, var(--xiaoai-sidebar-start), var(--xiaoai-sidebar-end));
}

.auth-logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 56px;
  color: var(--xiaoai-ink-strong);
  font-size: 17px;
  font-weight: 760;
  letter-spacing: 0.02em;
  text-decoration: none;
}

.auth-logo img {
  width: 38px;
  height: 38px;
  padding: 6px;
  border: 1px solid rgba(96, 165, 250, 0.22);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.08);
}

.auth-brand h1 {
  max-width: 470px;
  margin: 0;
  font-size: clamp(34px, 4.4vw, 48px);
  font-weight: 780;
  letter-spacing: -0.045em;
  line-height: 1.04;
}

.auth-brand p {
  max-width: 440px;
  margin: 20px 0 0;
  color: var(--xiaoai-ink);
  font-size: 14px;
  line-height: 1.75;
}

.auth-highlights {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 36px;
}

.auth-highlight {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 9px 11px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.22);
  color: var(--xiaoai-ink);
  font-size: 12px;
  font-weight: 620;
}

.auth-highlight svg {
  flex: 0 0 auto;
  color: var(--xiaoai-cyan-soft);
}

.auth-card {
  display: flex;
  align-items: center;
  padding: 42px;
  background: color-mix(in srgb, var(--ep-bg-color) 96%, transparent);
}

.auth-card > * {
  width: 100%;
}

@media (max-width: 900px) {
  .auth-panel {
    min-height: 0;
    grid-template-columns: 1fr;
  }

  .auth-brand {
    padding: 34px 28px;
  }

  .auth-logo {
    margin-bottom: 28px;
  }

  .auth-highlights {
    display: none;
  }

  .auth-card {
    padding: 28px 22px 32px;
  }
}

@media (max-width: 520px) {
  .auth-page {
    padding: 0;
  }

  .auth-panel {
    min-height: 100vh;
    border: 0;
    border-radius: 0;
  }
}
</style>
