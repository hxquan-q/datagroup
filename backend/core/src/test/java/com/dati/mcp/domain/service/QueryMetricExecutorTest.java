package com.dati.mcp.domain.service;

import com.dati.common.template.HandlebarsStyleParser;
import com.dati.common.template.PreparedSql;
import com.dati.common.template.SqlRenderer;
import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolConfig;
import com.dati.mcp.domain.model.ToolError;
import com.dati.semantic.domain.model.MetricDimension;
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

    @Test
    @DisplayName("duplicate codes across scoped subjects are ambiguous")
    void duplicateCodesAreAmbiguous() {
        McpServiceSnapshot.MetricDraft second = new McpServiceSnapshot.MetricDraft(
            "metric-2", "subject-2", "ds-2", "quality_daily_fpy", "Other", null,
            null, null, null, null, null, List.of(), "SELECT 2", List.of(), 30, 1000, 1);
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.QUERY_METRIC, new ToolConfig.QueryMetricConfig(),
            new com.dati.mcp.domain.model.param.QueryMetricArgs("quality_daily_fpy", Map.of(), List.of()),
            List.of(), List.of(metric(), second));

        assertThatThrownBy(() -> executor.execute(ctx))
            .isInstanceOf(ToolExecuteException.class)
            .hasMessageContaining("ambiguous");
        verifyNoInteractions(parameterizedSqlExecutor);
    }

    @Test
    @DisplayName("nested dimensions cannot bypass the top-level allowlist")
    void rejectsNestedDimensions() {
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.QUERY_METRIC, new ToolConfig.QueryMetricConfig(),
            new com.dati.mcp.domain.model.param.QueryMetricArgs(
                "quality_daily_fpy", Map.of("dimensions", List.of("factory")), List.of()),
            List.of(), List.of(metric()));

        assertThatThrownBy(() -> executor.execute(ctx))
            .isInstanceOf(ToolExecuteException.class)
            .hasMessageContaining("top-level dimensions");
        verifyNoInteractions(parameterizedSqlExecutor);
    }

    @Test
    @DisplayName("raw metric placeholders are rejected before caller input can alter SQL")
    void rejectsRawPlaceholders() {
        McpServiceSnapshot.MetricDraft rawMetric = new McpServiceSnapshot.MetricDraft(
            "metric-raw", "subject-1", "ds-1", "raw_metric", "Raw", null,
            null, null, null, null, null, List.of(), "SELECT {{{columns}}} FROM quality_daily", List.of(), 30, 1000, 1);
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.QUERY_METRIC, new ToolConfig.QueryMetricConfig(),
            new com.dati.mcp.domain.model.param.QueryMetricArgs(
                "raw_metric", Map.of("columns", "*"), List.of()),
            List.of(), List.of(rawMetric));

        assertThatThrownBy(() -> executor.execute(ctx))
            .isInstanceOf(ToolExecuteException.class)
            .hasMessageContaining("bound placeholders");
        verifyNoInteractions(parameterizedSqlExecutor);
    }
    @Test
    @DisplayName("structured metrics generate grouped SQL through confirmed one-hop relations")
    void structuredMetricGeneratesGroupedSql() {
        McpServiceSnapshot.MetricDraft structured = new McpServiceSnapshot.MetricDraft(
            "metric-structured", "subject-1", "ds-1", "order_count", "Order count", null,
            List.of(), "owner", "day", "created_at", "Asia/Shanghai", List.of("customer"),
            List.of(new MetricDimension("customer", "public", "customers", "name", null)),
            "COUNT(*)", "public", "orders", null, List.of(), 30, 1000, 1);
        McpServiceSnapshot.TableRelationDraft relation = new McpServiceSnapshot.TableRelationDraft(
            "subject-1", "fact-id", "ds-1", "public", "orders", "customer_id",
            "dim-id", "ds-1", "public", "customers", "id", "MANY_TO_ONE", "owner", 1);
        SqlExecution sqlExecution = new SqlExecution("SELECT", List.of(), List.of());
        when(parameterizedSqlExecutor.execute(any())).thenReturn(sqlExecution);
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.QUERY_METRIC, new ToolConfig.QueryMetricConfig(),
            new com.dati.mcp.domain.model.param.QueryMetricArgs(
                "order_count", Map.of("start_time", "2026-01-01 00:00:00", "customer", "Alice"), List.of("customer")),
            List.of(), List.of(structured), List.of(relation));

        executor.execute(ctx);

        ArgumentCaptor<ToolExecutionContext> captor = ArgumentCaptor.forClass(ToolExecutionContext.class);
        verify(parameterizedSqlExecutor).execute(captor.capture());
        ToolConfig.ParamSqlConfig config = (ToolConfig.ParamSqlConfig) captor.getValue().config();
        assertThat(config.getSqlTemplate()).contains("d1.name AS customer");
        assertThat(config.getSqlTemplate()).contains("COUNT(*) AS metric_value");
        assertThat(config.getSqlTemplate()).contains("LEFT JOIN public.customers d1 ON f.customer_id = d1.id");
        assertThat(config.getSqlTemplate()).contains("f.created_at >= {{start_time}}");
        assertThat(config.getSqlTemplate()).contains("d1.name IN ({{customer}})");
        assertThat(config.getSqlTemplate()).contains("GROUP BY d1.name");
        assertThat(config.getParameters()).anyMatch(parameter -> "start_time".equals(parameter.getName())
            && "DateTime".equals(parameter.getType()));
        PreparedSql prepared = new SqlRenderer().render(new HandlebarsStyleParser().parse(config.getSqlTemplate()),
            Map.of("start_time", "2026-01-01 00:00:00", "customer", "Alice"));
        assertThat(prepared.sql()).contains("WHERE f.created_at >= ?");
        assertThat(prepared.sql()).contains("AND d1.name IN (?)");
        assertThat(prepared.bindings()).hasSize(2);
    }

    @Test
    @DisplayName("query metric arguments preserve explicit null values")
    void queryMetricArgumentsPreserveNullValues() {
        java.util.Map<String, Object> input = new java.util.LinkedHashMap<>();
        input.put("optional_filter", null);

        var args = new com.dati.mcp.domain.model.param.QueryMetricArgs("metric-1", input, null);

        assertThat(args.arguments()).containsEntry("optional_filter", null);
    }

    @Test
    @DisplayName("structured metrics can use row-preserving reverse one-hop relations")
    void structuredMetricUsesSafeReverseRelation() {
        McpServiceSnapshot.MetricDraft structured = new McpServiceSnapshot.MetricDraft(
            "metric-reverse", "subject-1", "ds-1", "customer_order_count", "Customer order count", null,
            List.of(), "owner", "day", "created_at", "Asia/Shanghai", List.of("order_status"),
            List.of(new MetricDimension("order_status", "public", "orders", "status", null)),
            "COUNT(*)", "public", "customers", null, List.of(), 30, 1000, 1);
        McpServiceSnapshot.TableRelationDraft relation = new McpServiceSnapshot.TableRelationDraft(
            "subject-1", "fact-id", "ds-1", "public", "orders", "customer_id",
            "dim-id", "ds-1", "public", "customers", "id", "ONE_TO_MANY", "owner", 1);
        when(parameterizedSqlExecutor.execute(any())).thenReturn(new SqlExecution("SELECT", List.of(), List.of()));
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.QUERY_METRIC, new ToolConfig.QueryMetricConfig(),
            new com.dati.mcp.domain.model.param.QueryMetricArgs("customer_order_count", Map.of(), List.of("order_status")),
            List.of(), List.of(structured), List.of(relation));

        executor.execute(ctx);

        ArgumentCaptor<ToolExecutionContext> captor = ArgumentCaptor.forClass(ToolExecutionContext.class);
        verify(parameterizedSqlExecutor).execute(captor.capture());
        ToolConfig.ParamSqlConfig config = (ToolConfig.ParamSqlConfig) captor.getValue().config();
        assertThat(config.getSqlTemplate()).contains("LEFT JOIN public.orders d1 ON f.id = d1.customer_id");
    }

    @Test
    @DisplayName("structured dimensions require a confirmed relation")
    void structuredMetricRejectsMissingRelation() {
        McpServiceSnapshot.MetricDraft structured = new McpServiceSnapshot.MetricDraft(
            "metric-structured", "subject-1", "ds-1", "order_count", "Order count", null,
            List.of(), "owner", "day", "created_at", "Asia/Shanghai", List.of("customer"),
            List.of(new MetricDimension("customer", "public", "customers", "name", null)),
            "COUNT(*)", "public", "orders", null, List.of(), 30, 1000, 1);
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.QUERY_METRIC, new ToolConfig.QueryMetricConfig(),
            new com.dati.mcp.domain.model.param.QueryMetricArgs(
                "order_count", Map.of(), List.of("customer")),
            List.of(), List.of(structured), List.of());

        assertThatThrownBy(() -> executor.execute(ctx))
            .isInstanceOf(ToolExecuteException.class)
            .hasMessageContaining("No confirmed one-hop table relation");
        verifyNoInteractions(parameterizedSqlExecutor);
    }

}
