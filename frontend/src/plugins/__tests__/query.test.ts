import { describe, expect, it, vi } from "vitest";

vi.mock("~/api/http", () => ({
  notifyError: vi.fn(),
}));

import { notifyError } from "~/api/http";
import { createXiaoaiQueryClient } from "../query";

describe("createXiaoaiQueryClient", () => {
  it("uses console-friendly query defaults", () => {
    const client = createXiaoaiQueryClient();

    expect(client.getDefaultOptions().queries).toMatchObject({
      retry: false,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    });
  });

  it("reports query failures through the shared API error notifier", async () => {
    const client = createXiaoaiQueryClient();
    const failure = new Error("request failed");

    await expect(
      client.fetchQuery({
        queryKey: ["test", "failure"],
        queryFn: async () => {
          throw failure;
        },
      }),
    ).rejects.toThrow("request failed");

    expect(notifyError).toHaveBeenCalledWith(failure, expect.any(String));
  });
});
