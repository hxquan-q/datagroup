package com.dati.mcp.domain.model.param;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** GET_SAMPLE_DATA tool parameters. */
public record GetSampleDataArgs(
    @NotNull(message = "is required") @NotBlank(message = "must not be blank")
    @JsonProperty("data_source_id") @JsonPropertyDescription("Data source ID from list_tables_and_terms or search results")
    String dataSourceId,

    @NotNull(message = "is required") @NotBlank(message = "must not be blank")
    @JsonProperty("table_name") @JsonPropertyDescription("Table name, optionally schema-qualified as schema.table")
    String tableName,

    @Size(max = 20)
    @JsonPropertyDescription("Optional column names to preview (max 20); omit for all columns")
    List<String> columns,

    @Min(1) @Max(20)
    @JsonPropertyDescription("Rows to preview, 1-20, default 10")
    Integer limit
) {
    public GetSampleDataArgs {
        columns = columns == null ? List.of() : List.copyOf(columns);
        limit = limit == null ? 10 : limit;
    }
}
