package com.dati.mcp.domain.model.param;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** QUERY_METRIC tool parameters. */
public record QueryMetricArgs(
    @NotNull(message = "is required") @NotBlank(message = "must not be blank")
    @JsonPropertyDescription("Metric ID, stable code, business name, or alias from search_semantics")
    String metric,

    @JsonPropertyDescription("Metric-specific parameter values, such as time range and filter values")
    Map<String, Object> arguments,

    @Size(max = 20, message = "must contain at most 20 items")
    @JsonPropertyDescription("Optional grouping dimensions. Every dimension must be allowed by the published metric definition")
    List<String> dimensions
) {
    public QueryMetricArgs {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
    }
}
