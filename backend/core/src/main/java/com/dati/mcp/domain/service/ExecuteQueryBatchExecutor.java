package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolConfig.ExecuteSqlConfig;
import com.dati.mcp.domain.model.param.ExecuteQueryBatchArgs;
import com.dati.mcp.domain.model.param.ExecuteSqlArgs;
import com.dati.mcp.server.pojo.BatchExecution;
import com.dati.mcp.server.pojo.SqlExecution;
import com.dati.mcp.server.pojo.ToolTestData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Runs an ordered batch of read-only statements in one call, reusing the execute_sql pipeline. */
@Component
public class ExecuteQueryBatchExecutor implements ToolExecutor {

    private final ExecuteSqlExecutor delegate;

    public ExecuteQueryBatchExecutor(ExecuteSqlExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public McpToolType getToolType() {
        return McpToolType.EXECUTE_QUERY_BATCH;
    }

    @Override
    public ToolTestData execute(ToolExecutionContext ctx) {
        ExecuteQueryBatchArgs args = ctx.args(ExecuteQueryBatchArgs.class);
        List<BatchExecution.StepResult> steps = new ArrayList<>();
        for (String sql : args.statements()) {
            if (sql == null || sql.isBlank()) {
                steps.add(new BatchExecution.StepResult(sql, false, null, "Statement must not be blank"));
                continue;
            }
            ToolExecutionContext stepCtx = new ToolExecutionContext(
                ctx.serviceId(), McpToolType.EXECUTE_SQL, ctx.config(),
                new ExecuteSqlArgs(args.dataSourceId(), sql, null),
                ctx.scopeItems(), ctx.metrics(), ctx.relations());
            try {
                SqlExecution execution = (SqlExecution) delegate.execute(stepCtx);
                steps.add(new BatchExecution.StepResult(sql, true, execution, null));
            } catch (ToolExecuteException e) {
                steps.add(new BatchExecution.StepResult(sql, false, null, e.getMessage()));
            }
        }
        return new BatchExecution(List.copyOf(steps));
    }
}
