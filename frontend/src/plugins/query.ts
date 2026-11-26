import { QueryCache, QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import type { App } from "vue";
import { notifyError } from "~/api/http";
import { i18n } from "~/plugins/i18n";

export function createXiaoaiQueryClient(): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache({
      onError: (error) => {
        notifyError(error, i18n.global.t("common.loadFailed"));
      },
    }),
    defaultOptions: {
      queries: {
        retry: false,
        staleTime: 30_000,
        refetchOnWindowFocus: false,
      },
    },
  });
}

export const queryClient = createXiaoaiQueryClient();

export function setupVueQuery(app: App): void {
  app.use(VueQueryPlugin, { queryClient });
}
