package com.dati.mcp.server.pojo;

import com.dati.datasource.domain.model.DataSourceDef;
import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.semantic.domain.model.TermDef;
import java.util.List;

public record SearchHit(List<String> keywords, List<DataSourceDef> dataSources,
                         List<TermDef> terms,
                         List<McpServiceSnapshot.TableRelationDraft> relations) implements ToolTestData {
    public SearchHit(List<String> keywords, List<DataSourceDef> dataSources, List<TermDef> terms) {
        this(keywords, dataSources, terms, List.of());
    }
}
