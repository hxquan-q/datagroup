package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolConfig;
import com.dati.mcp.domain.model.ToolError;
import com.dati.semantic.domain.model.MetricParameter;
import com.dati.mcp.server.pojo.MetricExecution;
import com.dati.mcp.server.pojo.SqlExecution;
import com.dati.mcp.server.pojo.StatementResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryMetricExecutor tests")
class QueryMetricExecutorTest {

    @Mock
    private ParameterizedSqlExecutor parameterizedSqlExecutor;

    private QueryMetricExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new QueryMetricExecutor(parameterizedSqlExecutor);
    }

    private McpServiceSnapshot.MetricDraft metric() {
        return new McpServiceSnapshot.MetricDraft(
            "metric-1", "subject-1", "ds-1", "quality_daily_fpy", "质量日报 FPY",
            "Quality daily first pass yield", List.of("FPY", "质量合格率"), "quality-owner",
            "day", "FDate", "Asia/Shanghai", List.of("workshop", "model"),
            "SELECT * FROM quality_daily WHERE date >= {{start_date}}",
            List.of(new MetricParameter("start_date", "String", true, null, "Start date")),
            30, 1000, 3);
    }

    @Test
    @DisplayName("queries a frozen metric by stable code and preserves metric evidence")
    void queriesMetricByCode() {
        SqlExecution sqlExecution = new SqlExecution("SELECT * FROM quality_daily WHERE date >= ?",
            List.of(StatementResult.select(List.of("fpy"), List.of(List.of(95.46)), false)),
            List.of("2026-09-01"));
        when(parameterizedSqlExecutor.execute(any())).thenReturn(sqlExecution);

        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.QUERY_METRIC, new ToolConfig.QueryMetricConfig(),
            new com.dati.mcp.domain.model.param.QueryMetricArgs(
                "quality_daily_fpy", Map.of("start_date", "2026-09-01"), List.of("workshop")),
            List.of(), List.of(metric()));

        MetricExecution result = (MetricExecution) executor.execute(ctx);

        assertThat(result.metric().metricId()).isEqualTo("metric-1");
        assertThat(result.metric().code()).isEqualTo("quality_daily_fpy");
        assertThat(result.metric().revision()).isEqualTo(3);
        assertThat(result.parameters()).containsEntry("start_date", "2026-09-01");
        assertThat(result.execution()).isSameAs(sqlExecution);

        ArgumentCaptor<ToolExecutionContext> captor = ArgumentCaptor.forClass(ToolExecutionContext.class);
        verify(parameterizedSqlExecutor).execute(captor.capture());
        ToolExecutionContext delegated = captor.getValue();
        ToolConfig.ParamSqlConfig config = (ToolConfig.ParamSqlConfig) delegated.config();
        assertThat(config.getDataSourceId()).isEqualTo("ds-1");
        assertThat(config.getSqlTemplate()).contains("quality_daily");
        assertThat(delegated.argumentsMap()).containsEntry("start_date", "2026-09-01");
        assertThat(delegated.argumentsMap()).containsEntry("dimensions", List.of("workshop"));
    }

    @Test
    @DisplayName("unknown metric is a parameter error")
    void unknownMetricFails() {
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.QUERY_METRIC, new ToolConfig.QueryMetricConfig(),
            new com.dati.mcp.domain.model.param.QueryMetricArgs("missing", Map.of(), List.of()),
            List.of(), List.of(metric()));

        assertThatThrownBy(() -> executor.execute(ctx))
            .isInstanceOf(ToolExecuteException.class)
            .satisfies(e -> assertThat(((ToolExecuteException) e).getToolError()).isEqualTo(ToolError.PARAM_INVALID));
        verifyNoInteractions(parameterizedSqlExecutor);
    }

    @Test
    @DisplayName("dimensions outside the published metric contract are rejected")
    void rejectsUnpublishedDimension() {
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.QUERY_METRIC, new ToolConfig.QueryMetricConfig(),
            new com.dati.mcp.domain.model.param.QueryMetricArgs(
                "quality_daily_fpy", Map.of("start_date", "2026-09-01"), List.of("factory")),
            List.of(), List.of(metric()));

        assertThatThrownBy(() -> executor.execute(ctx))
            .isInstanceOf(ToolExecuteException.class)
            .hasMessageContaining("factory");
        verifyNoInteractions(parameterizedSqlExecutor);
    }
}
