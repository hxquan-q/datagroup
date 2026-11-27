package com.dati.mcp.server.pojo;

import java.util.List;

public record SampleData(
    String table,
    List<String> columns,
    List<List<Object>> rows,
    List<String> maskedColumns
) implements ToolTestData {
}
