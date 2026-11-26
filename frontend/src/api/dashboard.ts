import type { components } from "./openapi.types";
import { get } from "./http";

export type DashboardDailyCount = components["schemas"]["DailyCountVO"];
export type DashboardResourceOverview = components["schemas"]["ResourceOverviewVO"];
export type DashboardOverviewVO = components["schemas"]["DashboardOverviewVO"];

export function getDashboardOverview(
  days: number = 15,
  signal?: AbortSignal,
): Promise<DashboardOverviewVO> {
  return get<DashboardOverviewVO>("/v1/dashboard/overview", { days }, signal);
}
