import type { BaseResourceVO, IdResponse, PageResponse } from "~/api/types";
import { del, get, post, put } from "./http";

export interface TableRelationVO extends BaseResourceVO {
  subject_id: string;
  fact_table_id: string;
  fact_table_name?: string;
  fact_column: string;
  dimension_table_id: string;
  dimension_table_name?: string;
  dimension_column: string;
  cardinality: string;
  owner?: string;
  revision?: number;
}

export interface TableRelationPayload {
  fact_table_id?: string;
  fact_column?: string;
  dimension_table_id?: string;
  dimension_column?: string;
  cardinality?: string;
  owner?: string;
  description?: string;
}

export function listTableRelations(subjectId: string, page = 1, size = 20): Promise<PageResponse<TableRelationVO>> {
  return get<PageResponse<TableRelationVO>>(`/v1/subjects/${encodeURIComponent(subjectId)}/relations`, { page, size });
}

export function createTableRelation(subjectId: string, body: TableRelationPayload): Promise<IdResponse> {
  return post<IdResponse, TableRelationPayload>(`/v1/subjects/${encodeURIComponent(subjectId)}/relations`, body);
}

export function updateTableRelation(id: string, body: TableRelationPayload): Promise<IdResponse> {
  return put<IdResponse, TableRelationPayload>(`/v1/relations/${encodeURIComponent(id)}`, body);
}

export function deleteTableRelation(id: string): Promise<IdResponse> {
  return del<IdResponse>(`/v1/relations/${encodeURIComponent(id)}`);
}
