import { describe, expect, it } from "vitest";
import type { DailyStatItem } from "~/api/mcp-usage";
import { fillDailyTrendGaps } from "../usage-stats";

function row(date: string, successCalls: number): DailyStatItem {
  return {
    date,
    total_calls: successCalls,
    success_calls: successCalls,
    failed_calls: 0,
    success_rate: 100,
    avg_duration_ms: 12,
  };
}

describe("fillDailyTrendGaps", () => {
  it("fills idle days between active dates in chronological order", () => {
    const rows = fillDailyTrendGaps([row("2026-01-03", 3), row("2026-01-01", 1)]);

    expect(rows.map((item) => item.date)).toEqual([
      "2026-01-01",
      "2026-01-02",
      "2026-01-03",
    ]);
    expect(rows[1]).toMatchObject({
      total_calls: 0,
      success_calls: 0,
      failed_calls: 0,
      success_rate: null,
    });
  });

  it("keeps a single active day unchanged", () => {
    expect(fillDailyTrendGaps([row("2026-01-01", 1)])).toHaveLength(1);
  });
});
