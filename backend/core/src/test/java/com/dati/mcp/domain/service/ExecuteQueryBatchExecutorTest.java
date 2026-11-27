package com.dati.mcp.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolError;
import com.dati.mcp.domain.model.param.ExecuteQueryBatchArgs;
import com.dati.mcp.server.pojo.BatchExecution;
import com.dati.mcp.server.pojo.SqlExecution;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecuteQueryBatchExecutorTest {

    private ToolExecutionContext ctx(ExecuteQueryBatchArgs args) {
        return new ToolExecutionContext("svc", McpToolType.EXECUTE_QUERY_BATCH, null, args, List.of());
    }

    @Test
    @DisplayName("executes each statement and collects per-step results")
    void executesStatements() {
        ExecuteSqlExecutor delegate = mock(ExecuteSqlExecutor.class);
        when(delegate.execute(any())).thenReturn(mock(SqlExecution.class));
        ExecuteQueryBatchExecutor executor = new ExecuteQueryBatchExecutor(delegate);

        BatchExecution result = (BatchExecution) executor.execute(
            ctx(new ExecuteQueryBatchArgs("ds-1", List.of("SELECT 1", "SELECT 2"))));

        assertThat(result.steps()).hasSize(2);
        assertThat(result.steps()).allMatch(BatchExecution.StepResult::success);
    }

    @Test
    @DisplayName("captures step errors without aborting the batch")
    void capturesStepErrors() {
        ExecuteSqlExecutor delegate = mock(ExecuteSqlExecutor.class);
        when(delegate.execute(any()))
            .thenThrow(new ToolExecuteException(ToolError.SQL_EXECUTION_ERROR, "boom"))
            .thenReturn(mock(SqlExecution.class));
        ExecuteQueryBatchExecutor executor = new ExecuteQueryBatchExecutor(delegate);

        BatchExecution result = (BatchExecution) executor.execute(
            ctx(new ExecuteQueryBatchArgs("ds-1", List.of("SELECT bad", "SELECT 1"))));

        assertThat(result.steps()).hasSize(2);
        assertThat(result.steps().get(0).success()).isFalse();
        assertThat(result.steps().get(0).error()).contains("boom");
        assertThat(result.steps().get(1).success()).isTrue();
    }
}
