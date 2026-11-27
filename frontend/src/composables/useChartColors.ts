import { computed, ref } from "vue";
import { useMutationObserver } from "@vueuse/core";
import { isDark } from "./dark";

export function useChartColors() {
  const themeVersion = ref(0);

  if (typeof window !== "undefined") {
    useMutationObserver(
      document.documentElement,
      () => {
        themeVersion.value += 1;
      },
      { attributes: true, attributeFilter: ["class"] },
    );
  }

  return computed(() => {
    void themeVersion.value;
    const fallback = {
      primary: "#2563eb",
      success: "#16a34a",
      danger: "#dc2626",
      warning: "#d97706",
      textPrimary: isDark.value ? "#e5e7eb" : "#1f2937",
      textSecondary: isDark.value ? "#9ca3af" : "#6b7280",
      border: isDark.value ? "#374151" : "#e5e7eb",
      background: isDark.value ? "#111827" : "#ffffff",
    };

    if (typeof window === "undefined") {
      return fallback;
    }

    const styles = getComputedStyle(document.documentElement);
    const cssVar = (name: string, fallbackValue: string) =>
      styles.getPropertyValue(name).trim() || fallbackValue;

    return {
      primary: cssVar("--ep-color-primary", fallback.primary),
      success: cssVar("--ep-color-success", fallback.success),
      danger: cssVar("--ep-color-danger", fallback.danger),
      warning: cssVar("--ep-color-warning", fallback.warning),
      textPrimary: cssVar("--ep-text-color-primary", fallback.textPrimary),
      textSecondary: cssVar("--ep-text-color-secondary", fallback.textSecondary),
      border: cssVar("--ep-border-color-lighter", fallback.border),
      background: cssVar("--ep-bg-color", fallback.background),
    };
  });
}
