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
import com.dati.datasource.repository.dao.TableInfoDAO;
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
