package com.dati.mcp.domain.model.param;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** EXECUTE_QUERY_BATCH tool parameters. */
public record ExecuteQueryBatchArgs(
    @NotNull(message = "is required") @NotBlank(message = "must not be blank")
    @JsonProperty("data_source_id") @JsonPropertyDescription("Data source ID from list_tables_and_terms or search results")
    String dataSourceId,

    @NotNull(message = "is required") @Size(min = 1, max = 10)
    @JsonPropertyDescription("Ordered read-only SELECT statements to run in one call (max 10), e.g. multi-step attribution analysis")
    List<String> statements
) {
    public ExecuteQueryBatchArgs {
        statements = statements == null ? List.of() : List.copyOf(statements);
    }
}
