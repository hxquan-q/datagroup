import { describe, expect, it } from "vitest";
import { datasourceKeys } from "../query-keys";

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
