package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolConfig;
import com.dati.mcp.domain.model.param.InspectMetricArgs;
import com.dati.mcp.server.pojo.MetricInspection;
import com.dati.semantic.domain.model.MetricDimension;
import com.dati.semantic.domain.model.MetricParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InspectMetricExecutor tests")
class InspectMetricExecutorTest {

    private final InspectMetricExecutor executor = new InspectMetricExecutor();

    private McpServiceSnapshot.MetricDraft structuredMetric() {
        return new McpServiceSnapshot.MetricDraft(
            "metric-1", "subject-1", "ds-1", "sinter_pass_count", "烧结良品数",
            "Sintering pass count", List.of("烧结pass"), "quality-owner",
            "day", "FCreateTime", "Asia/Shanghai", List.of("station"),
            List.of(new MetricDimension("station", "mes", "t_mes_station", "FName", "站点")),
            "COUNT(*)", "mes", "t_station_pass",
            null,
            List.of(new MetricParameter("mo_no", "String", true, null, "工单号")),
            30, 1000, 5);
    }

    private McpServiceSnapshot.TableRelationDraft relation() {
        return new McpServiceSnapshot.TableRelationDraft(
            "subject-1", "fact-1", "ds-1", "mes", "t_station_pass", "FStationId",
            "dim-1", "ds-1", "mes", "t_mes_station", "F_id", "MANY_TO_ONE", "owner", 2);
    }

    private ToolExecutionContext ctx(String key) {
        return new ToolExecutionContext(
            "svc-1", McpToolType.INSPECT_METRIC, new ToolConfig.QueryMetricConfig(),
            new InspectMetricArgs(key), List.of(), List.of(structuredMetric()), List.of(relation()));
    }

    @Test
    @DisplayName("inspects a structured metric with dimensions, joins, parameters, and example")
    void inspectsStructuredMetric() {
        MetricInspection result = (MetricInspection) executor.execute(ctx("sinter_pass_count"));

        assertThat(result.metric().metricId()).isEqualTo("metric-1");
        assertThat(result.metric().structured()).isTrue();
        assertThat(result.metric().measureExpression()).isEqualTo("COUNT(*)");
        assertThat(result.metric().factTableName()).isEqualTo("t_station_pass");
        assertThat(result.metric().revision()).isEqualTo(5);
        assertThat(result.dimensions()).singleElement().satisfies(dimension -> {
            assertThat(dimension.name()).isEqualTo("station");
            assertThat(dimension.tableName()).isEqualTo("t_mes_station");
            assertThat(dimension.column()).isEqualTo("FName");
        });
        assertThat(result.parameters()).singleElement().satisfies(parameter -> {
            assertThat(parameter.name()).isEqualTo("mo_no");
            assertThat(parameter.required()).isTrue();
        });
        assertThat(result.joinPaths()).singleElement().satisfies(join -> {
            assertThat(join.factTable()).isEqualTo("t_station_pass");
            assertThat(join.dimensionTable()).isEqualTo("t_mes_station");
            assertThat(join.cardinality()).isEqualTo("MANY_TO_ONE");
        });
        assertThat(result.example().tool()).isEqualTo("query_metric");
        assertThat(result.example().arguments()).containsEntry("metric", "metric-1");
        assertThat(result.example().arguments()).containsEntry("dimensions", List.of("station"));
    }

    @Test
    @DisplayName("resolves metrics by alias case-insensitively")
    void resolvesByAlias() {
        MetricInspection result = (MetricInspection) executor.execute(ctx("烧结pass"));
        assertThat(result.metric().code()).isEqualTo("sinter_pass_count");
    }

    @Test
    @DisplayName("unknown metric is a parameter error")
    void unknownMetric() {
        assertThatThrownBy(() -> executor.execute(ctx("missing")))
            .isInstanceOf(ToolExecuteException.class)
            .hasMessageContaining("Metric not found");
    }
}
