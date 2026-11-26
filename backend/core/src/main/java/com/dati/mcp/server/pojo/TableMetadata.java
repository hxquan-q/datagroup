package com.dati.mcp.server.pojo;

import com.dati.datasource.domain.model.TableDef;
import com.dati.mcp.domain.model.McpServiceSnapshot;
import java.util.List;

public record TableMetadata(List<TableDef> tables,
                            List<McpServiceSnapshot.TableRelationDraft> relations) implements ToolTestData {
    public TableMetadata(List<TableDef> tables) {
        this(tables, List.of());
    }
}
