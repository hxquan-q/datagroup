import { describe, expect, it } from "vitest";
import { datasourceKeys, mcpUsageKeys } from "../query-keys";

describe("datasource query keys", () => {
  it("creates stable list keys from pagination and keyword state", () => {
    expect(datasourceKeys.list(2, 20, "orders")).toEqual([
      "datasources",
      "list",
      { page: 2, size: 20, keyword: "orders" },
    ]);
  });

  it("shares the list prefix used for invalidation", () => {
    const key = datasourceKeys.list(1, 10, "");
    expect(key.slice(0, 2)).toEqual(datasourceKeys.lists());
  });
});

describe("mcp usage query keys", () => {
  it("scopes stats and logs by service", () => {
    expect(mcpUsageKeys.stats("svc-1", 15)).toEqual([
      "mcp-usage",
      "stats",
      "svc-1",
      15,
    ]);
    expect(mcpUsageKeys.logs("svc-1", 2, 20, "query", false)).toEqual([
      "mcp-usage",
      "logs",
      "svc-1",
      { page: 2, size: 20, toolName: "query", success: false },
    ]);
  });
});
