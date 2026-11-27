import type { BaseResourceVO, IdResponse, PageResponse } from "~/api/types";
import { del, get, post, put } from "./http";

export interface ValidationCaseVO extends BaseResourceVO {
  subject_id: string;
  metric_id: string;
  question: string;
  arguments?: Record<string, unknown>;
  dimensions?: string[];
  assertion_type?: string;
  expected_value?: string;
  owner?: string;
  enabled?: boolean;
  last_status?: "PASS" | "FAIL" | "ERROR" | string;
  last_message?: string;
  last_run_at?: string;
  last_duration_ms?: number;
  last_metric_revision?: number;
  last_evidence?: Record<string, unknown>;
}

export interface ValidationCasePayload {
  name: string;
  description?: string;
  metric_id?: string;
  question?: string;
  arguments?: Record<string, unknown>;
  dimensions?: string[];
  assertion_type?: string;
  expected_value?: string;
  owner?: string;
  enabled?: boolean;
}

export function listValidationCases(
  subjectId: string,
  page = 1,
  size = 20,
  signal?: AbortSignal,
): Promise<PageResponse<ValidationCaseVO>> {
  return get<PageResponse<ValidationCaseVO>>(
    `/v1/subjects/${encodeURIComponent(subjectId)}/validation-cases`,
    { page, size },
    signal,
  );
}

export function createValidationCase(subjectId: string, body: ValidationCasePayload, signal?: AbortSignal): Promise<IdResponse> {
  return post<IdResponse, ValidationCasePayload>(
    `/v1/subjects/${encodeURIComponent(subjectId)}/validation-cases`,
    body,
    signal,
  );
}

export function updateValidationCase(id: string, body: ValidationCasePayload, signal?: AbortSignal): Promise<IdResponse> {
  return put<IdResponse, ValidationCasePayload>(`/v1/validation-cases/${encodeURIComponent(id)}`, body, signal);
}

export function runValidationCase(id: string, signal?: AbortSignal): Promise<ValidationCaseVO> {
  return post<ValidationCaseVO>(`/v1/validation-cases/${encodeURIComponent(id)}/run`, undefined, signal);
}

export function listPublishBlockingMessages(subjectIds: string[], signal?: AbortSignal): Promise<string[]> {
  return post<string[], string[]>("/v1/validation-cases/publish-blocking", subjectIds, signal);
}

export function deleteValidationCase(id: string, signal?: AbortSignal): Promise<IdResponse> {
  return del<IdResponse>(`/v1/validation-cases/${encodeURIComponent(id)}`, undefined, signal);
}
