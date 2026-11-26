import type { BaseResourceVO, IdResponse, PageResponse } from "~/api/types";
import { del, get, post, put } from "./http";

export interface MetricDimension {
  name: string;
  schema?: string;
  table_name?: string;
  tableName?: string;
  column: string;
  description?: string;
}

export interface MetricParameter {
  name: string;
  type?: string;
  required?: boolean;
  default_value?: string;
  defaultValue?: string;
  description?: string;
}

export interface MetricVO extends BaseResourceVO {
  subject_id: string;
  data_source_id: string;
  code: string;
  aliases?: string[];
  owner?: string;
  grain?: string;
  time_field?: string;
  time_zone?: string;
  allowed_dimensions?: string[];
  dimension_mappings?: MetricDimension[];
  measure_expression?: string;
  fact_schema?: string;
  fact_table_name?: string;
  sql_template?: string;
  parameters?: MetricParameter[];
  timeout?: number;
  max_rows?: number;
  revision?: number;
}

export interface MetricPayload {
  code?: string;
  name: string;
  description?: string;
  aliases?: string[];
  owner?: string;
  grain?: string;
  time_field?: string;
  time_zone?: string;
  allowed_dimensions?: string[];
  dimension_mappings?: MetricDimension[];
  measure_expression?: string;
  fact_schema?: string;
  fact_table_name?: string;
  sql_template: string;
  parameters?: MetricParameter[];
  timeout?: number;
  max_rows?: number;
}

export function listMetrics(
  subjectId: string,
  page = 1,
  size = 20,
  keyword?: string,
  signal?: AbortSignal,
): Promise<PageResponse<MetricVO>> {
  return get<PageResponse<MetricVO>>(
    `/v1/subjects/${encodeURIComponent(subjectId)}/metrics`,
    { page, size, keyword },
    signal,
  );
}

export function createMetric(subjectId: string, body: MetricPayload, signal?: AbortSignal): Promise<IdResponse> {
  return post<IdResponse, MetricPayload>(
    `/v1/subjects/${encodeURIComponent(subjectId)}/metrics`,
    body,
    signal,
  );
}

export function updateMetric(id: string, body: MetricPayload, signal?: AbortSignal): Promise<IdResponse> {
  return put<IdResponse, MetricPayload>(`/v1/metrics/${encodeURIComponent(id)}`, body, signal);
}

export function deleteMetric(id: string, signal?: AbortSignal): Promise<IdResponse> {
  return del<IdResponse>(`/v1/metrics/${encodeURIComponent(id)}`, undefined, signal);
}
