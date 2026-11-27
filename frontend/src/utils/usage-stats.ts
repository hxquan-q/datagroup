import type { DailyStatItem } from "~/api/mcp-usage";

export interface DailyTrendRow extends Omit<DailyStatItem, "success_rate"> {
  success_rate: number | null;
}

const DAY_MS = 24 * 60 * 60 * 1000;

function toUtcDay(date: string): number {
  return Date.parse(`${date}T00:00:00Z`);
}

export function fillDailyTrendGaps(items: DailyStatItem[]): DailyTrendRow[] {
  const sorted = [...items].sort((a, b) => a.date.localeCompare(b.date));
  if (sorted.length < 2) {
    return sorted;
  }

  const rowsByDate = new Map(sorted.map((item) => [item.date, item]));
  const start = toUtcDay(sorted[0].date);
  const end = toUtcDay(sorted[sorted.length - 1].date);
  const rows: DailyTrendRow[] = [];

  for (let timestamp = start; timestamp <= end; timestamp += DAY_MS) {
    const date = new Date(timestamp).toISOString().slice(0, 10);
    rows.push(
      rowsByDate.get(date) ?? {
        date,
        total_calls: 0,
        success_calls: 0,
        failed_calls: 0,
        success_rate: null,
        avg_duration_ms: 0,
      },
    );
  }

  return rows;
}
