package com.dati.mcp.domain.model.param;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** INSPECT_METRIC tool parameters. */
public record InspectMetricArgs(
    @NotNull(message = "is required") @NotBlank(message = "must not be blank")
    @JsonPropertyDescription("Metric ID, stable code, business name, or alias from search_semantics")
    String metric
) {
}
