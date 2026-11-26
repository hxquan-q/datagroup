package com.dati.semantic.domain.service;

import com.dati.TestFixtures;
import com.dati.auth.authentication.User;
import com.dati.base.RequestContext;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.model.ValidationCase;
import com.dati.semantic.repository.dao.MetricDAO;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.dao.SubjectTableDAO;
import com.dati.semantic.repository.dao.ValidationCaseDAO;
import com.dati.datasource.repository.dao.DataSourceDAO;
import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.DataSourcePO;
import com.dati.mcp.domain.service.ParameterizedSqlExecutor;
import com.dati.mcp.server.pojo.SqlExecution;
import com.dati.mcp.server.pojo.StatementResult;
import com.dati.semantic.repository.po.MetricPO;
import com.dati.semantic.repository.po.SubjectPO;
import com.dati.semantic.repository.po.ValidationCasePO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidationCaseService unit tests")
class ValidationCaseServiceTest {
    @Mock private ValidationCaseDAO validationCaseDAO;
    @Mock private SubjectDAO subjectDAO;
    @Mock private MetricDAO metricDAO;
    @Mock private PermissionService permissionService;
    @Mock private SubjectTableDAO subjectTableDAO;
    @Mock private TableInfoDAO tableInfoDAO;
    @Mock private DataSourceDAO dataSourceDAO;
    @Mock private ParameterizedSqlExecutor parameterizedSqlExecutor;
    @InjectMocks private ValidationCaseService service;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(TestFixtures.TEST_USER_ID);
        user.setName(TestFixtures.TEST_USER_ID);
        RequestContext.setUser(user);
        SubjectPO subject = new SubjectPO();
        subject.setId("subject-1");
        subject.setCreatedBy(TestFixtures.TEST_USER_ID);
        subject.setDatasourceId("ds-1");
        MetricPO metric = new MetricPO();
        metric.setId("metric-1");
        metric.setSubjectId("subject-1");
        metric.setRevision(1);
        metric.setDataSourceId("ds-1");
        metric.setSqlTemplate("SELECT 1");
        metric.setParameters(java.util.List.of());
        metric.setTimeout(30);
        metric.setMaxRows(1000);
        lenient().when(subjectDAO.findById("subject-1")).thenReturn(Optional.of(subject));
        lenient().when(metricDAO.findById("metric-1")).thenReturn(Optional.of(metric));
        lenient().doAnswer(inv -> null).when(permissionService).requireCurrentUser(any(), anyString(), any(), anyString());
        lenient().when(validationCaseDAO.save(any(ValidationCasePO.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(dataSourceDAO.existsById("ds-1")).thenReturn(true);
    }

    @AfterEach
    void tearDown() { RequestContext.getContext().clear(); }

    @Test
    @DisplayName("create binds the case to the subject and metric")
    void createBindsSubjectAndMetric() {
        ValidationCase item = new ValidationCase();
        item.setMetricId("metric-1");
        item.setQuestion("质量日报直通率是多少？");
        item.setName("FPY regression");

        ValidationCase created = service.createCase("subject-1", item);

        assertThat(created.getSubjectId()).isEqualTo("subject-1");
        assertThat(created.getMetricId()).isEqualTo("metric-1");
        assertThat(created.getEnabled()).isTrue();
    }
    @Test
    @DisplayName("run passes when execution succeeds and rows are returned for NON_EMPTY")
    void runPassesNonEmpty() {
        ValidationCasePO existing = existingCase("NON_EMPTY");
        when(validationCaseDAO.findById("case-1")).thenReturn(Optional.of(existing));
        when(parameterizedSqlExecutor.execute(any())).thenReturn(new SqlExecution("SELECT 1",
            java.util.List.of(StatementResult.select(java.util.List.of("close_rate"), java.util.List.of(java.util.List.of(28.16)), false)),
            java.util.List.of()));

        ValidationCase result = service.runCase("case-1");

        assertThat(result.getLastStatus()).isEqualTo("PASS");
        assertThat(result.getLastMetricRevision()).isEqualTo(1);
        assertThat(result.getLastEvidence().get("returned_rows")).isEqualTo(1);
    }

    @Test
    @DisplayName("run fails assertion when NON_EMPTY returns zero rows")
    void runFailsWhenEmpty() {
        ValidationCasePO existing = existingCase("NON_EMPTY");
        when(validationCaseDAO.findById("case-1")).thenReturn(Optional.of(existing));
        when(parameterizedSqlExecutor.execute(any())).thenReturn(new SqlExecution("SELECT 1",
            java.util.List.of(StatementResult.select(java.util.List.of("close_rate"), java.util.List.of(), false)),
            java.util.List.of()));

        ValidationCase result = service.runCase("case-1");

        assertThat(result.getLastStatus()).isEqualTo("FAIL");
        assertThat(result.getLastMessage()).contains("expected non-empty result");
    }


    @Test
    @DisplayName("run records an error when case dimensions are not allowed by the metric")
    void runErrorsWhenDimensionNotAllowed() {
        ValidationCasePO existing = existingCase("EXECUTION_SUCCESS");
        existing.setDimensions(java.util.List.of("workshop_id"));
        MetricPO metric = new MetricPO();
        metric.setId("metric-1");
        metric.setSubjectId("subject-1");
        metric.setRevision(2);
        metric.setDataSourceId("ds-1");
        metric.setSqlTemplate("SELECT 1");
        metric.setParameters(java.util.List.of());
        metric.setAllowedDimensions(java.util.List.of("org_id"));
        metric.setTimeout(30);
        metric.setMaxRows(1000);
        when(metricDAO.findById("metric-1")).thenReturn(Optional.of(metric));
        when(validationCaseDAO.findById("case-1")).thenReturn(Optional.of(existing));

        ValidationCase result = service.runCase("case-1");

        assertThat(result.getLastStatus()).isEqualTo("ERROR");
        assertThat(result.getLastMessage()).contains("dimensions are not allowed");
    }


    @Test
    @DisplayName("row count assertion fails when the query result is truncated")
    void rowCountFailsWhenTruncated() {
        ValidationCasePO existing = existingCase("ROW_COUNT");
        existing.setExpectedValue("1");
        when(validationCaseDAO.findById("case-1")).thenReturn(Optional.of(existing));
        when(parameterizedSqlExecutor.execute(any())).thenReturn(new SqlExecution("SELECT 1",
            java.util.List.of(StatementResult.select(java.util.List.of("close_rate"), java.util.List.of(java.util.List.of(28.16)), true)),
            java.util.List.of()));

        ValidationCase result = service.runCase("case-1");

        assertThat(result.getLastStatus()).isEqualTo("FAIL");
        assertThat(result.getLastMessage()).contains("truncated");
    }

    @Test
    @DisplayName("editing executable inputs clears stale validation results")
    void editingExecutableInputsClearsLastResult() {
        ValidationCasePO existing = existingCase("NON_EMPTY");
        existing.setLastStatus("PASS");
        existing.setLastMessage("execution returned rows");
        existing.setLastRunAt(java.time.Instant.now());
        existing.setLastDurationMs(12L);
        existing.setLastMetricRevision(1);
        existing.setLastEvidence(java.util.Map.of("returned_rows", 1));
        when(validationCaseDAO.findById("case-1")).thenReturn(Optional.of(existing));
        ValidationCase patch = new ValidationCase();
        patch.setArguments(java.util.Map.of("org_id", 4));

        ValidationCase result = service.updateCase("case-1", patch);

        assertThat(result.getLastStatus()).isNull();
        assertThat(result.getLastRunAt()).isNull();
        assertThat(result.getLastEvidence()).isEmpty();
    }

    @Test
    @DisplayName("stored sample rows are redacted from users without data source access")
    void sampleRowsAreRedactedWithoutDataSourceAccess() {
        ValidationCasePO existing = existingCase("NON_EMPTY");
        existing.setLastEvidence(java.util.Map.of("returned_rows", 1, "sample_rows", java.util.List.of(java.util.List.of("secret"))));
        DataSourcePO dataSource = new DataSourcePO();
        dataSource.setId("ds-1");
        dataSource.setCreatedBy("someone-else");
        when(validationCaseDAO.findById("case-1")).thenReturn(Optional.of(existing));
        when(dataSourceDAO.findById("ds-1")).thenReturn(Optional.of(dataSource));

        ValidationCase result = service.getCase("case-1");

        assertThat(result.getLastEvidence()).doesNotContainKey("sample_rows");
        assertThat(result.getLastEvidence()).containsEntry("sample_rows_redacted", true);
    }


    @Test
    @DisplayName("run records an error when the metric data source was deleted")
    void runErrorsWhenDataSourceDeleted() {
        ValidationCasePO existing = existingCase("EXECUTION_SUCCESS");
        when(validationCaseDAO.findById("case-1")).thenReturn(Optional.of(existing));
        when(dataSourceDAO.existsById("ds-1")).thenReturn(false);

        ValidationCase result = service.runCase("case-1");

        assertThat(result.getLastStatus()).isEqualTo("ERROR");
        assertThat(result.getLastMessage()).contains("Data source not found");
    }

    @Test
    @DisplayName("a deleted data source invalidates an older passing result in case reads")
    void deletedDataSourceInvalidatesOldPass() {
        ValidationCasePO existing = existingCase("EXECUTION_SUCCESS");
        existing.setLastStatus("PASS");
        existing.setLastMessage("execution succeeded");
        when(validationCaseDAO.findById("case-1")).thenReturn(Optional.of(existing));
        when(dataSourceDAO.findById("ds-1")).thenReturn(Optional.empty());

        ValidationCase result = service.getCase("case-1");

        assertThat(result.getLastStatus()).isEqualTo("ERROR");
        assertThat(result.getLastMessage()).contains("data source is unavailable");
    }

    @Test
    @DisplayName("question-only edits keep the existing validation result")
    void questionOnlyEditKeepsLastResult() {
        ValidationCasePO existing = existingCase("NON_EMPTY");
        existing.setLastStatus("PASS");
        existing.setLastMessage("execution returned rows");
        when(validationCaseDAO.findById("case-1")).thenReturn(Optional.of(existing));
        ValidationCase patch = new ValidationCase();
        patch.setQuestion("修改后的业务问法");

        ValidationCase result = service.updateCase("case-1", patch);

        assertThat(result.getLastStatus()).isEqualTo("PASS");
        assertThat(result.getLastMessage()).isEqualTo("execution returned rows");
    }

    @Test
    @DisplayName("ignored expected values for non-row-count assertions do not clear results")
    void ignoredExpectedValueKeepsLastResult() {
        ValidationCasePO existing = existingCase("NON_EMPTY");
        existing.setLastStatus("PASS");
        when(validationCaseDAO.findById("case-1")).thenReturn(Optional.of(existing));
        ValidationCase patch = new ValidationCase();
        patch.setExpectedValue("");

        ValidationCase result = service.updateCase("case-1", patch);

        assertThat(result.getLastStatus()).isEqualTo("PASS");
    }

    private ValidationCasePO existingCase(String assertionType) {
        ValidationCasePO po = new ValidationCasePO();
        po.setId("case-1");
        po.setSubjectId("subject-1");
        po.setMetricId("metric-1");
        po.setName("close rate regression");
        po.setQuestion("广西二部工单关闭率");
        po.setArguments(java.util.Map.of("org_id", 14, "start", "2026-10-01 00:00:00", "end", "2026-10-02 00:00:00"));
        po.setAssertionType(assertionType);
        po.setEnabled(true);
        return po;
    }

}
