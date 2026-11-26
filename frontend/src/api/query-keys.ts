export const datasourceKeys = {
  all: ["datasources"] as const,
  lists: () => [...datasourceKeys.all, "list"] as const,
  list: (page: number, size: number, keyword: string) =>
    [...datasourceKeys.lists(), { page, size, keyword }] as const,
  detail: (id: string) => [...datasourceKeys.all, "detail", id] as const,
};

export const mcpUsageKeys = {
  all: ["mcp-usage"] as const,
  stats: (serviceId: string, days: number) =>
    [...mcpUsageKeys.all, "stats", serviceId, days] as const,
  logs: (
    serviceId: string,
    page: number,
    size: number,
    toolName: string,
    success: boolean | null,
  ) =>
    [
      ...mcpUsageKeys.all,
      "logs",
      serviceId,
      { page, size, toolName, success },
    ] as const,
};
