import type { BaseResourceVO, IdResponse, PageResponse } from "~/api/types.ts";
import type { components } from "~/api/openapi.types";
import { del, get, post, put } from "./http";

type OpenapiDatasourceVO = Omit<
  components["schemas"]["DatasourceVO"],
  "id" | "name" | "jdbc_url" | "username" | "type"
>;

export type DatasourceVO = OpenapiDatasourceVO & BaseResourceVO & {
  jdbc_url: string;
  username: string;
  type: string;
};

// Create/update payload; edit-mode connection tests include id so the backend can reuse the stored password
export interface DataSourcePayload {
  id?: string;
  name?: string;
  description?: string;
  jdbc_url: string;
  username: string;
  password?: string;
  type: string;
}

// Table metadata
export interface TableVO {
  name: string;
  comment?: string;
}

// Test a datasource connection
// Request fields use snake_case because they are serialized directly as API JSON
export function testConnection(
  body: DataSourcePayload,
  signal?: AbortSignal,
): Promise<boolean> {
  return post<boolean, DataSourcePayload>(
    "/v1/data-sources/test-connection",
    body,
    signal,
  );
}

// Create a datasource
export function addDataSource(
  body: DataSourcePayload,
  signal?: AbortSignal,
): Promise<IdResponse> {
  return post<IdResponse, DataSourcePayload>("/v1/data-sources", body, signal);
}

// Update a datasource
export function updateDataSource(
  id: string,
  body: DataSourcePayload,
  signal?: AbortSignal,
): Promise<IdResponse> {
  return put<IdResponse, DataSourcePayload>(
    `/v1/data-sources/${encodeURIComponent(id)}`,
    body,
    signal,
  );
}

// Delete a datasource
export function deleteDataSource(
  id: string,
  signal?: AbortSignal,
): Promise<IdResponse> {
  return del<IdResponse>(
    `/v1/data-sources/${encodeURIComponent(id)}`,
    undefined,
    signal,
  );
}

// List datasources with pagination
export function listDataSources(
  page: number,
  size: number,
  keyword?: string,
  signal?: AbortSignal,
): Promise<PageResponse<DatasourceVO>> {
  return get<PageResponse<DatasourceVO>>(
    "/v1/data-sources",
    { page, size, keyword },
    signal,
  );
}

// Get datasource details
export function getDataSource(
  id: string,
  signal?: AbortSignal,
): Promise<DatasourceVO> {
  return get<DatasourceVO>(
    `/v1/data-sources/${encodeURIComponent(id)}`,
    undefined,
    signal,
  );
}

// List schemas
export function getSchemas(
  id: string,
  signal?: AbortSignal,
): Promise<string[]> {
  return get<string[]>(
    `/v1/data-sources/${encodeURIComponent(id)}/schemas`,
    undefined,
    signal,
  );
}

// List tables in a schema
export function getTables(
  id: string,
  schema: string,
  signal?: AbortSignal,
): Promise<TableVO[]> {
  return get<TableVO[]>(
    `/v1/data-sources/${encodeURIComponent(id)}/schemas/${encodeURIComponent(schema)}/tables`,
    undefined,
    signal,
  );
}
