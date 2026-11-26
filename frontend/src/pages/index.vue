<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import {
  ArrowUpRight,
  BookOpen,
  Cable,
  Database,
  Layers,
  ShieldCheck,
  Sparkles,
  SquareTerminal,
  Zap,
} from "@lucide/vue";
import { listDataSources } from "~/api/datasource";
import { listSubjects } from "~/api/subject";
import { listMcpServices } from "~/api/mcp-service";

const { t, locale } = useI18n();
const router = useRouter();

const docLink = computed(() => (locale.value === "en" ? "/docs/en/" : "/docs/"));
const loadingStats = ref(true);
const datasourceCount = ref(0);
const subjectCount = ref(0);
const mcpServiceCount = ref(0);
const featuredService = ref<{ name?: string; endpoint_path?: string; status?: string } | null>(null);

async function fetchOverview() {
  loadingStats.value = true;
  try {
    const [dsRes, subRes, mcpRes] = await Promise.allSettled([
      listDataSources(1, 1),
      listSubjects(1, 1),
      listMcpServices(1, 1),
    ]);

    if (dsRes.status === "fulfilled") datasourceCount.value = dsRes.value.total ?? 0;
    if (subRes.status === "fulfilled") subjectCount.value = subRes.value.total ?? 0;
    if (mcpRes.status === "fulfilled") {
      mcpServiceCount.value = mcpRes.value.total ?? 0;
      featuredService.value = mcpRes.value.data?.[0] || null;
    }
  } finally {
    loadingStats.value = false;
  }
}

const modules = computed(() => [
  {
    id: "datasource",
    icon: Database,
    title: t("home.workflow.step1Title"),
    desc: t("home.workflow.step1Desc"),
    statLabel: t("home.stats.dataSources"),
    count: datasourceCount.value,
    path: "/datasources",
  },
  {
    id: "subject",
    icon: Layers,
    title: t("home.workflow.step2Title"),
    desc: t("home.workflow.step2Desc"),
    statLabel: t("home.stats.subjects"),
    count: subjectCount.value,
    path: "/subjects",
  },
  {
    id: "mcp",
    icon: SquareTerminal,
    title: t("home.workflow.step3Title"),
    desc: t("home.workflow.step3Desc"),
    statLabel: t("home.stats.mcpServices"),
    count: mcpServiceCount.value,
    path: "/mcp-services",
  },
]);

const features = computed(() => [
  {
    icon: ShieldCheck,
    title: t("home.features.security"),
    desc: t("home.features.securityDesc"),
  },
  {
    icon: Sparkles,
    title: t("home.features.semanticPrecision"),
    desc: t("home.features.semanticPrecisionDesc"),
  },
  {
    icon: Cable,
    title: t("home.features.flexibleIntegration"),
    desc: t("home.features.flexibleIntegrationDesc"),
  },
]);

function go(path: string) {
  router.push(path);
}

onMounted(fetchOverview);
</script>

<template>
  <div class="home-page">
    <section class="hero-panel">
      <div class="hero-copy">
        <div class="eyebrow">
          <span class="eyebrow-dot"></span>
          {{ t("home.consoleLabel") }}
        </div>
        <h1>{{ t("home.heroTitle") }}</h1>
        <p>{{ t("home.heroDesc") }}</p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="go('/datasources')">
            {{ t("home.primaryAction") }}
          </el-button>
          <el-button size="large" plain :icon="BookOpen" tag="a" :href="docLink" target="_blank" rel="noopener">
            {{ t("home.docLink") }}
          </el-button>
        </div>
      </div>

      <div class="hero-visual">
        <div class="visual-glow visual-glow-a"></div>
        <div class="visual-glow visual-glow-b"></div>
        <div class="signal-card">
          <div class="signal-card-header">
            <span></span><span></span><span></span>
          </div>
          <div v-if="loadingStats" class="signal-service signal-empty">
            <div class="service-icon"><Zap :size="17" /></div>
            <div class="signal-service-copy">
              <span class="text-skeleton text-skeleton-title"></span>
              <span class="text-skeleton text-skeleton-endpoint"></span>
            </div>
          </div>
          <div v-else-if="featuredService" class="signal-service">
            <div class="service-icon"><Zap :size="17" /></div>
            <div class="signal-service-copy">
              <strong>{{ featuredService.name }}</strong>
              <small>{{ featuredService.endpoint_path }}</small>
            </div>
            <em>{{ featuredService.status }}</em>
          </div>
          <div v-else class="signal-service signal-empty">
            <div class="service-icon"><Zap :size="17" /></div>
            <div class="signal-service-copy">
              <strong>{{ t("home.noServiceTitle") }}</strong>
              <small>{{ t("home.noServiceHint") }}</small>
            </div>
          </div>
          <div class="signal-grid">
            <div><b>{{ datasourceCount }}</b><span>{{ t("home.stats.dataSources") }}</span></div>
            <div><b>{{ subjectCount }}</b><span>{{ t("home.stats.subjects") }}</span></div>
            <div><b>{{ mcpServiceCount }}</b><span>{{ t("home.stats.mcpServices") }}</span></div>
          </div>
        </div>
      </div>
    </section>

    <section class="module-grid" :aria-label="t('home.modulesLabel')">
      <article
        v-for="module in modules"
        :key="module.id"
        class="module-card"
        role="link"
        tabindex="0"
        @click="go(module.path)"
        @keydown.enter.prevent="go(module.path)"
      >
        <div class="module-topline">
          <span class="module-icon"><component :is="module.icon" :size="20" /></span>
          <ArrowUpRight class="module-arrow" :size="17" />
        </div>
        <div class="module-count">
          <span v-if="loadingStats" class="count-skeleton"></span>
          <template v-else>{{ module.count }}</template>
        </div>
        <h2>{{ module.statLabel }}</h2>
        <p>{{ module.desc }}</p>
      </article>
    </section>

    <section class="workflow-panel">
      <div class="section-heading">
        <div>
          <span class="section-kicker">{{ t("home.workflowKicker") }}</span>
          <h2>{{ t("home.workflow.title") }}</h2>
          <p>{{ t("home.workflowSubtitle") }}</p>
        </div>
      </div>
      <div class="workflow-steps">
        <button
          v-for="(module, index) in modules"
          :key="module.id"
          type="button"
          class="workflow-step"
          @click="go(module.path)"
        >
          <span class="step-index">0{{ index + 1 }}</span>
          <span class="step-icon"><component :is="module.icon" :size="18" /></span>
          <span class="step-copy">
            <strong>{{ module.title }}</strong>
            <small>{{ module.desc }}</small>
          </span>
        </button>
      </div>
    </section>

    <section class="feature-grid">
      <article v-for="feature in features" :key="feature.title" class="feature-card">
        <span class="feature-icon"><component :is="feature.icon" :size="17" /></span>
        <div>
          <h3>{{ feature.title }}</h3>
          <p>{{ feature.desc }}</p>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.home-page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 24px 28px 40px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-panel {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
  gap: 28px;
  overflow: hidden;
  padding: 28px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  border-radius: 24px;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.12), transparent 42%),
    linear-gradient(180deg, var(--ep-bg-color), color-mix(in srgb, var(--ep-bg-color) 82%, var(--ep-color-primary-light-9)));
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.08);
}

:global(html.dark) .hero-panel {
  border-color: rgba(96, 165, 250, 0.18);
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.22), transparent 46%),
    linear-gradient(180deg, rgba(15, 23, 42, 0.92), rgba(2, 6, 23, 0.94));
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.32);
}

.hero-copy {
  align-self: center;
  max-width: 590px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid rgba(37, 99, 235, 0.15);
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.08);
  color: var(--ep-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.eyebrow-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--xiaoai-cyan-bright);
  box-shadow: 0 0 0 5px rgba(34, 211, 238, 0.12);
}

.hero-copy h1 {
  margin: 16px 0 12px;
  color: var(--ep-text-color-primary);
  font-size: clamp(30px, 4vw, 40px);
  font-weight: 780;
  letter-spacing: -0.045em;
  line-height: 1.04;
}

.hero-copy p {
  margin: 0;
  max-width: 560px;
  color: var(--ep-text-color-regular);
  font-size: 16px;
  line-height: 1.75;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 22px;
}

.hero-actions :deep(.el-button),
.hero-actions :deep(.ep-button) {
  height: 42px;
  padding-inline: 18px;
  border-radius: 999px;
  font-weight: 650;
}

.hero-visual {
  position: relative;
  min-height: 216px;
  display: grid;
  place-items: center;
}

.visual-glow {
  position: absolute;
  border-radius: 999px;
  filter: blur(8px);
}

.visual-glow-a {
  width: 220px;
  height: 220px;
  background: rgba(37, 99, 235, 0.16);
  transform: translate(-64px, -52px);
}

.visual-glow-b {
  width: 180px;
  height: 180px;
  background: rgba(6, 182, 212, 0.17);
  transform: translate(88px, 82px);
}

.signal-card {
  position: relative;
  width: min(390px, 100%);
  padding: 18px;
  border: 1px solid rgba(96, 165, 250, 0.22);
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.94), rgba(2, 6, 23, 0.96));
  box-shadow: 0 30px 80px rgba(2, 6, 23, 0.34), inset 0 1px 0 rgba(255,255,255,0.08);
  color: var(--xiaoai-ink);
}

.signal-card-header {
  display: flex;
  gap: 6px;
  margin-bottom: 18px;
}

.signal-card-header span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.4);
}

.signal-card-header span:first-child { background: var(--xiaoai-cyan-bright); }

.signal-service {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.72);
}

.service-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--xiaoai-blue), var(--xiaoai-cyan));
  color: white;
}

.signal-service-copy {
  min-width: 0;
}

.signal-service strong,
.signal-service small {
  display: block;
  overflow-wrap: anywhere;
}

.signal-service strong { font-size: 14px; }
.signal-service small { margin-top: 3px; color: var(--xiaoai-ink-muted); font-family: ui-monospace, monospace; font-size: 11px; overflow-wrap: anywhere; word-break: break-word; }
.signal-service em { padding: 4px 7px; border-radius: 999px; background: rgba(16, 185, 129, 0.13); color: var(--xiaoai-success-soft); font-size: 10px; font-style: normal; font-weight: 800; white-space: nowrap; }

.signal-empty {
  grid-template-columns: auto minmax(0, 1fr);
}

.text-skeleton {
  display: block;
  border-radius: 6px;
  background: rgba(148, 163, 184, 0.2);
}

.text-skeleton-title {
  width: 132px;
  height: 14px;
}

.text-skeleton-endpoint {
  width: 176px;
  max-width: 100%;
  height: 11px;
  margin-top: 7px;
}

.signal-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 12px;
}

.signal-grid div {
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
  background: rgba(30, 41, 59, 0.5);
}

.signal-grid b,
.signal-grid span {
  display: block;
}

.signal-grid b { color: var(--xiaoai-cyan-soft); font-size: 20px; }
.signal-grid span { margin-top: 3px; color: var(--xiaoai-ink-muted); font-size: 11px; }


.module-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.module-card {
  position: relative;
  min-height: 188px;
  padding: 20px;
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 24px;
  background: color-mix(in srgb, var(--ep-bg-color) 90%, transparent);
  box-shadow: 0 14px 40px rgba(15, 23, 42, 0.05);
  cursor: pointer;
  transition: transform var(--xiaoai-motion), border-color var(--xiaoai-motion), box-shadow var(--xiaoai-motion);
}

.module-card:hover {
  transform: translateY(-3px);
  border-color: rgba(37, 99, 235, 0.32);
  box-shadow: 0 22px 52px rgba(37, 99, 235, 0.11);
}

.module-card:focus-visible {
  outline: 2px solid var(--ep-color-primary);
  outline-offset: 3px;
}

.module-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.module-icon,
.feature-icon,
.step-icon {
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(37,99,235,.12), rgba(6,182,212,.1));
  color: var(--ep-color-primary);
}

.module-icon {
  width: 42px;
  height: 42px;
}

.module-arrow {
  color: var(--ep-text-color-placeholder);
  transition: transform var(--xiaoai-motion), color var(--xiaoai-motion);
}

.module-card:hover .module-arrow {
  color: var(--ep-color-primary);
  transform: translate(2px, -2px);
}

.module-count {
  margin-top: 16px;
  color: var(--ep-text-color-primary);
  font-size: 32px;
  font-weight: 760;
  letter-spacing: -0.04em;
  line-height: 1;
}

.count-skeleton {
  display: inline-block;
  width: 54px;
  height: 34px;
  border-radius: 8px;
  background: var(--ep-fill-color);
}

.module-card h2 {
  margin: 8px 0 0;
  color: var(--ep-text-color-secondary);
  font-size: 13px;
  font-weight: 650;
}

.module-card p {
  margin: 10px 0 12px;
  color: var(--ep-text-color-regular);
  font-size: 13px;
  line-height: 1.65;
}


.workflow-panel {
  padding: 24px;
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 24px;
  background: color-mix(in srgb, var(--ep-bg-color) 92%, transparent);
}

.section-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.section-kicker {
  color: var(--ep-color-primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.section-heading h2 {
  margin: 5px 0 0;
  color: var(--ep-text-color-primary);
  font-size: 20px;
  font-weight: 720;
  letter-spacing: -0.02em;
}

.section-heading p {
  margin: 6px 0 0;
  color: var(--ep-text-color-secondary);
  font-size: 13px;
}

.workflow-steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.workflow-step {
  display: grid;
  grid-template-columns: auto auto 1fr;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 16px;
  background: var(--ep-fill-color-lighter);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: all var(--xiaoai-motion);
}

.workflow-step:hover {
  border-color: rgba(37, 99, 235, 0.28);
  background: var(--ep-color-primary-light-9);
}

.step-index {
  color: var(--ep-text-color-placeholder);
  font-family: ui-monospace, monospace;
  font-size: 11px;
  font-weight: 800;
}

.step-icon {
  width: 34px;
  height: 34px;
}

.step-copy strong,
.step-copy small {
  display: block;
}

.step-copy strong {
  color: var(--ep-text-color-primary);
  font-size: 13px;
}

.step-copy small {
  margin-top: 3px;
  color: var(--ep-text-color-secondary);
  font-size: 11px;
  line-height: 1.45;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.feature-card {
  display: flex;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--ep-border-color-lighter);
  border-radius: 20px;
  background: color-mix(in srgb, var(--ep-bg-color) 84%, transparent);
}

.feature-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
}

.feature-card h3 {
  margin: 1px 0 5px;
  color: var(--ep-text-color-primary);
  font-size: 13px;
  font-weight: 700;
}

.feature-card p {
  margin: 0;
  color: var(--ep-text-color-secondary);
  font-size: 12px;
  line-height: 1.55;
}

@media (max-width: 1180px) {
  .hero-panel {
    padding: 24px;
  }

  .hero-copy h1 {
    font-size: clamp(28px, 3.5vw, 36px);
  }
}

@media (max-width: 980px) {
  .hero-panel {
    grid-template-columns: 1fr;
  }

  .hero-visual {
    min-height: 210px;
  }

  .module-grid,
  .workflow-steps,
  .feature-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .home-page {
    padding: 20px 14px 32px;
  }

  .hero-panel {
    padding: 28px 20px;
    border-radius: 22px;
  }

  .hero-copy h1 {
    font-size: 32px;
  }
}
</style>
