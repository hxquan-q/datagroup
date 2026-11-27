package com.dati.mcp.server.pojo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SqlExecution.class, name = "SQL_EXECUTION"),
    @JsonSubTypes.Type(value = TableMetadata.class, name = "TABLE_METADATA"),
    @JsonSubTypes.Type(value = SearchHit.class, name = "SEARCH_HIT"),
    @JsonSubTypes.Type(value = MetadataUpdateData.class, name = "METADATA_UPDATE"),
    @JsonSubTypes.Type(value = TableListData.class, name = "TABLE_LIST"),
    @JsonSubTypes.Type(value = SemanticCatalog.class, name = "SEMANTIC_CATALOG"),
    @JsonSubTypes.Type(value = MetricExecution.class, name = "METRIC_EXECUTION"),
    @JsonSubTypes.Type(value = MetricInspection.class, name = "METRIC_INSPECTION"),
    @JsonSubTypes.Type(value = SampleData.class, name = "SAMPLE_DATA"),
    @JsonSubTypes.Type(value = BatchExecution.class, name = "BATCH_EXECUTION"),
})
public interface ToolTestData {}
