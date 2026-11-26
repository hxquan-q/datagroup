export const datasourceKeys = {
  all: ["datasources"] as const,
  lists: () => [...datasourceKeys.all, "list"] as const,
  list: (page: number, size: number, keyword: string) =>
    [...datasourceKeys.lists(), { page, size, keyword }] as const,
  detail: (id: string) => [...datasourceKeys.all, "detail", id] as const,
};
