import { post } from "./http";

export interface ToolTestRequest {
  arguments: Record<string, any>;
}

export interface ToolTestResponse {
  success: boolean;
  execution_time_ms: number;
  data?: ToolTestData;
  error?: ToolTestError;
}

export interface ToolTestError {
  error_category:
    | "PARAM_ERROR"
    | "SCOPE_ERROR"
    | "PERMISSION_DENIED"
    | "SQL_ERROR"
    | "TIMEOUT";
  message: string;
}

export type ToolTestData =
  | SqlExecution
  | TableMetadata
  | SearchHit
  | MetadataUpdateData
  | TableListData
  | SemanticCatalog
  | MetricExecution
  | MetricInspection
  | SampleData
  | BatchExecution;

export interface SemanticCatalog {
  type: "SEMANTIC_CATALOG";
  metrics: SemanticMetric[];
}

export interface SemanticMetric {
  metric_id: string;
  subject_id: string;
  code: string;
  name: string;
  description?: string;
  aliases?: string[];
  owner?: string;
  grain?: string;
  time_field?: string;
  time_zone?: string;
  allowed_dimensions?: string[];
  revision?: number;
}

export interface SampleData {
  type: "SAMPLE_DATA";
  table: string;
  columns: string[];
  rows: any[][];
  masked_columns?: string[];
}

export interface BatchExecution {
  type: "BATCH_EXECUTION";
  steps: { sql: string; success: boolean; execution?: SqlExecution; error?: string }[];
}

export interface MetricInspection {
  type: "METRIC_INSPECTION";
  metric: {
    metric_id: string;
    subject_id: string;
    code: string;
    name: string;
    description?: string;
    aliases?: string[];
    owner?: string;
    grain?: string;
    time_field?: string;
    time_zone?: string;
    structured: boolean;
    measure_expression?: string;
    fact_schema?: string;
    fact_table_name?: string;
    revision?: number;
  };
  dimensions: { name: string; schema?: string; table_name?: string; column?: string; description?: string }[];
  parameters: { name: string; type?: string; required: boolean; default_value?: string; description?: string }[];
  join_paths: { fact_schema?: string; fact_table: string; fact_column: string; dimension_schema?: string; dimension_table: string; dimension_column: string; cardinality?: string }[];
  example: { tool: string; arguments: Record<string, any> };
}

export interface MetricExecution {
  type: "METRIC_EXECUTION";
  metric: {
    metric_id: string;
    subject_id: string;
    code: string;
    name: string;
    owner?: string;
    grain?: string;
    time_field?: string;
    time_zone?: string;
    allowed_dimensions?: string[];
    revision?: number;
  };
  parameters: Record<string, any>;
  dimensions: string[];
  execution: SqlExecution;
}

// ── METADATA_UPDATE (UPDATE_TABLE_INFO / UPDATE_COLUMN_INFO / UPSERT_TERM) ──

export interface MetadataUpdateData {
  type: "METADATA_UPDATE";
  results: MetadataUpdateResult[];
}

export interface MetadataUpdateResult {
  entity_type: "TABLE" | "COLUMN" | "TERM";
  entity: string;
  success: boolean;
  change_type?: "CREATE" | "UPDATE";
  old?: { description?: string; aliases?: string[] };
  new?: { description?: string; aliases?: string[] };
  error?: { error_category: string; message: string };
}

export interface SqlExecution {
  type: "SQL_EXECUTION";
  executed_sql: string;
  bindings?: any[];
  results: StatementResult[];
}

export interface TableMetadata {
  type: "TABLE_METADATA";
  tables: TableDef[];
}

export interface SearchHit {
  type: "SEARCH_HIT";
  keywords: string[];
  data_sources: DataSourceDef[];
  terms: TermDef[];
  metrics?: SemanticMetric[];
  relations?: TableRelationPath[];
}

export interface SemanticMetric {
  metric_id: string;
  subject_id: string;
  code: string;
  name: string;
  description?: string;
  aliases?: string[];
  owner?: string;
  grain?: string;
  time_field?: string;
  time_zone?: string;
  allowed_dimensions?: string[];
  revision?: number;
  call_example?: {
    metric: string;
    arguments: Record<string, unknown>;
    dimensions: string[];
  };
}

export interface TableRelationPath {
  fact_table_name: string;
  fact_schema?: string;
  fact_column: string;
  dimension_table_name: string;
  dimension_schema?: string;
  dimension_column: string;
  cardinality: string;
}

export interface TableListData {
  type: "TABLE_LIST";
  data_sources: DataSourceDef[];
  terms?: TermDef[];
}

export interface DataSourceDef {
  id: string;
  name: string;
  db_type?: string;
  default_schema?: string;
  description?: string;
  tables: TableDef[];
}

export interface TermDef {
  name: string;
  description: string;
  subject_name: string;
}

export type StatementResult = SelectResult | WriteResult;

export interface SelectResult {
  success: true;
  type: "SELECT";
  columns: string[];
  rows: any[][];
  /** Rows returned in this response; equals the row limit when truncated. */
  returned_rows: number;
  truncated: boolean;
  /** Present only when truncated: explains the limit to the caller. */
  notice?: string;
  error_message?: string;
}

export interface WriteResult {
  success: boolean;
  type: "WRITE";
  affected_rows?: number;
  error_message?: string;
}

export interface TableDef {
  table: string;
  schema?: string;
  description?: string;
  aliases?: string[];
  columns?: ColumnDef[];
}

export interface ColumnDef {
  name: string;
  type: string;
  comment: string;
  aliases?: string[];
  sample_values?: string[];
}

export function testTool(
  serviceId: string,
  toolId: string,
  body: ToolTestRequest,
  signal?: AbortSignal,
): Promise<ToolTestResponse> {
  return post<ToolTestResponse, ToolTestRequest>(
    `/v1/mcp-services/${encodeURIComponent(serviceId)}/tools/${encodeURIComponent(toolId)}/test`,
    body,
    signal,
  );
}
