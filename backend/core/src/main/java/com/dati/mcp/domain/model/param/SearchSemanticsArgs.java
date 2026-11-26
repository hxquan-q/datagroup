package com.dati.mcp.domain.model.param;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Size;

import java.util.List;

/** SEARCH_SEMANTICS tool parameters. */
public record SearchSemanticsArgs(
    @Size(max = 10, message = "must contain at most 10 items")
    @JsonPropertyDescription("Business keywords, metric names, aliases, or questions fragments. Empty returns the published metric catalog")
    List<String> keywords
) {
    public SearchSemanticsArgs {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
