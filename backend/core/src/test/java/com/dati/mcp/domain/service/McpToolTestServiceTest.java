package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolConfig.ExecuteSqlConfig;
import com.dati.mcp.domain.model.ToolError;
import com.dati.mcp.repository.dao.McpServiceDataScopeDAO;
import com.dati.mcp.server.pojo.SqlExecution;
import com.dati.mcp.server.pojo.ToolTestData;
import com.dati.mcp.server.pojo.ToolTestRequest;
import com.dati.mcp.server.pojo.ToolTestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("McpToolTestService unit tests")
class McpToolTestServiceTest {

    @Mock
    private ToolResolver toolResolver;

    @Mock
    private McpServiceDataScopeDAO scopeDAO;

    @Mock
    private ToolExecutor toolExecutor;

    @Mock
    private com.dati.permission.domain.service.PermissionService permissionService;

    @Mock
    private McpScopeResolver scopeResolver;

    @Mock
    private com.dati.semantic.domain.service.MetricService metricService;

    @Mock
    private McpTableRelationSnapshotService relationSnapshotService;

    private McpToolTestService service;

    @BeforeEach
    void setUp() {
        when(toolExecutor.getToolType()).thenReturn(McpToolType.EXECUTE_SQL);
        when(scopeResolver.resolve(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new McpToolTestService(toolResolver, scopeDAO, List.of(toolExecutor),
            new ToolParameterBinder(), permissionService, scopeResolver, metricService, relationSnapshotService);
    }

    @Test
    @DisplayName("Successful execution → returns success=true with data")
    void shouldReturnSuccessResponse() {
        ToolResolver.ResolvedTool resolved = new ToolResolver.ResolvedTool(
            McpToolType.EXECUTE_SQL, true, new ExecuteSqlConfig(), true);
        when(toolResolver.resolve("svc", "EXECUTE_SQL")).thenReturn(resolved);
        when(scopeDAO.findAllByServiceId("svc")).thenReturn(List.of());
        ToolTestData data = new SqlExecution("SELECT 1", List.of(), null);
        when(toolExecutor.execute(any())).thenReturn(data);

        ToolTestResponse resp = service.test("svc", "EXECUTE_SQL",
            new ToolTestRequest(Map.of("data_source_id", "ds-1", "sql", "SELECT 1")));

        assertThat(resp.success()).isTrue();
        assertThat(resp.data()).isEqualTo(data);
        assertThat(resp.error()).isNull();
        assertThat(resp.executionTimeMs()).isNotNegative();
    }

    @Test
    @DisplayName("executor throws RuntimeException → propagated, not caught")
    void shouldNotCatchRuntimeException() {
        ToolResolver.ResolvedTool resolved = new ToolResolver.ResolvedTool(
            McpToolType.EXECUTE_SQL, true, new ExecuteSqlConfig(), true);
        when(toolResolver.resolve("svc", "EXECUTE_SQL")).thenReturn(resolved);
        when(scopeDAO.findAllByServiceId("svc")).thenReturn(List.of());
        when(toolExecutor.execute(any())).thenThrow(new NullPointerException("boom"));

        assertThatThrownBy(() ->
            service.test("svc", "EXECUTE_SQL",
                new ToolTestRequest(Map.of("data_source_id", "ds-1", "sql", "SELECT 1"))))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("boom");
    }

    @Test
    @DisplayName("invalid arguments → ToolTestError with PARAM_ERROR category")
    void invalidArgumentsMapToParamError() {
        ToolResolver.ResolvedTool resolved = new ToolResolver.ResolvedTool(
            McpToolType.EXECUTE_SQL, true, new ExecuteSqlConfig(), true);
        when(toolResolver.resolve("svc", "EXECUTE_SQL")).thenReturn(resolved);
        when(scopeDAO.findAllByServiceId("svc")).thenReturn(List.of());

        ToolTestResponse resp = service.test("svc", "EXECUTE_SQL",
            new ToolTestRequest(Map.of("data_source_id", "ds-1")));

        assertThat(resp.success()).isFalse();
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().errorCategory()).isEqualTo("PARAM_ERROR");
    }

    @ParameterizedTest
    @CsvSource({
        "TOOL_NOT_FOUND, PARAM_ERROR",
        "TOOL_DISABLED, PARAM_ERROR",
        "PARAM_MISSING, PARAM_ERROR",
        "DATA_SOURCE_NOT_FOUND, PARAM_ERROR",
        "SCOPE_VIOLATION, SCOPE_ERROR",
        "SQL_POLICY_VIOLATION, PERMISSION_DENIED",
        "SQL_EXECUTION_ERROR, SQL_ERROR",
    })
    @DisplayName("ToolExecuteException → errorCategory mapped correctly")
    void shouldMapToolExecuteExceptionToCorrectCategory(ToolError toolError, String expectedCategory) {
        ToolResolver.ResolvedTool resolved = new ToolResolver.ResolvedTool(
            McpToolType.EXECUTE_SQL, true, new ExecuteSqlConfig(), true);
        when(toolResolver.resolve("svc", "EXECUTE_SQL")).thenReturn(resolved);
        when(scopeDAO.findAllByServiceId("svc")).thenReturn(List.of());
        when(toolExecutor.execute(any())).thenThrow(new ToolExecuteException(toolError, "test"));

        ToolTestResponse resp = service.test("svc", "EXECUTE_SQL",
            new ToolTestRequest(Map.of("data_source_id", "ds-1", "sql", "SELECT 1")));

        assertThat(resp.success()).isFalse();
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().errorCategory()).isEqualTo(expectedCategory);
    }
    @Test
    @DisplayName("metadata tests hide subject semantics the editor cannot read")
    void metadataTestsFilterUnreadableSubjects() {
        ToolExecutor searchExecutor = mock(ToolExecutor.class);
        when(searchExecutor.getToolType()).thenReturn(McpToolType.SEARCH_METADATA);
        McpToolTestService searchService = new McpToolTestService(toolResolver, scopeDAO, List.of(searchExecutor),
            new ToolParameterBinder(), permissionService, scopeResolver, metricService, relationSnapshotService);
        ToolResolver.ResolvedTool resolved = new ToolResolver.ResolvedTool(
            McpToolType.SEARCH_METADATA, true, new com.dati.mcp.domain.model.ToolConfig.SearchMetadataConfig(), true);
        when(toolResolver.resolve("svc", "SEARCH_METADATA")).thenReturn(resolved);
        com.dati.mcp.repository.po.McpServiceDataScopePO scope = new com.dati.mcp.repository.po.McpServiceDataScopePO();
        scope.setServiceId("svc");
        scope.setScopeType(com.dati.mcp.domain.model.McpDataScopeType.SUBJECT);
        scope.setReferenceId("subject-1");
        when(scopeDAO.findAllByServiceId("svc")).thenReturn(List.of(scope));
        when(permissionService.canSubject("subject-1", com.dati.permission.domain.model.Permission.VIEW)).thenReturn(false);
        when(metricService.getMetricsBySubjectIds(List.of())).thenReturn(List.of());
        when(relationSnapshotService.buildRelationDrafts(eq(List.of()), any())).thenReturn(List.of());
        ToolTestData data = new SqlExecution("SELECT 1", List.of(), null);
        when(searchExecutor.execute(any())).thenReturn(data);

        ToolTestResponse resp = searchService.test("svc", "SEARCH_METADATA",
            new ToolTestRequest(Map.of("keywords", List.of("quality"))));

        assertThat(resp.success()).isTrue();
        verify(metricService).getMetricsBySubjectIds(List.of());
        verify(relationSnapshotService).buildRelationDrafts(eq(List.of()), any());
    }

}
