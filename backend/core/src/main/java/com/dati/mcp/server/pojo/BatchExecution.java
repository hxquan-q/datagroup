package com.dati.mcp.server.pojo;

import java.util.List;

public record BatchExecution(List<StepResult> steps) implements ToolTestData {

    public record StepResult(String sql, boolean success, SqlExecution execution, String error) {
    }
}
