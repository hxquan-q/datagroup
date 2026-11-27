package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolConfig;
import com.dati.mcp.domain.model.param.GetSampleDataArgs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GetSampleDataExecutor validation tests")
class GetSampleDataExecutorTest {

    @Test
    @DisplayName("rejects injected identifiers before touching the database")
    void rejectsInjectedIdentifiers() {
        com.dati.datasource.domain.service.DataSourceService dsService =
            org.mockito.Mockito.mock(com.dati.datasource.domain.service.DataSourceService.class);
        GetSampleDataExecutor executor = new GetSampleDataExecutor(null, dsService);
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.GET_SAMPLE_DATA, new ToolConfig.QueryMetricConfig(),
            new GetSampleDataArgs("ds-1", "t_mo; DROP TABLE t_mo", List.of(), 5),
            List.of(), List.of(), List.of());
        assertThatThrownBy(() -> executor.execute(ctx))
            .isInstanceOf(ToolExecuteException.class);
    }

    @Test
    @DisplayName("rejects datasource that does not exist")
    void rejectsMissingDatasource() {
        com.dati.datasource.domain.service.DataSourceService dsService =
            org.mockito.Mockito.mock(com.dati.datasource.domain.service.DataSourceService.class);
        org.mockito.Mockito.when(dsService.getDataSourceInternal("missing"))
            .thenReturn(java.util.Optional.empty());
        GetSampleDataExecutor executor = new GetSampleDataExecutor(null, dsService);
        ToolExecutionContext ctx = new ToolExecutionContext(
            "svc-1", McpToolType.GET_SAMPLE_DATA, new ToolConfig.QueryMetricConfig(),
            new GetSampleDataArgs("missing", "t_mo", List.of(), 5),
            List.of(), List.of(), List.of());
        assertThatThrownBy(() -> executor.execute(ctx))
            .isInstanceOf(ToolExecuteException.class)
            .hasMessageContaining("Data source not found");
    }
}
