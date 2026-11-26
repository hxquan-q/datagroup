package com.dati.semantic.domain.service;

import com.dati.TestFixtures;
import com.dati.auth.authentication.User;
import com.dati.base.RequestContext;
import com.dati.base.exception.DatiException;
import com.dati.base.exception.ErrorCode;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.model.Metric;
import com.dati.semantic.repository.dao.MetricDAO;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.po.MetricPO;
import com.dati.semantic.repository.po.SubjectPO;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricService unit tests")
class MetricServiceTest {

    @Mock
    private MetricDAO metricDAO;

    @Mock
    private SubjectDAO subjectDAO;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private MetricService metricService;

    private SubjectPO subject;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(TestFixtures.TEST_USER_ID);
        user.setName(TestFixtures.TEST_USER_ID);
        RequestContext.setUser(user);
        subject = new SubjectPO();
        subject.setId("subject-1");
        subject.setCreatedBy(TestFixtures.TEST_USER_ID);
        subject.setDatasourceId("ds-1");
        lenient().when(subjectDAO.findById("subject-1")).thenReturn(Optional.of(subject));
        lenient().doAnswer(inv -> null).when(permissionService)
            .requireCurrentUser(any(), anyString(), any(), anyString());
        lenient().when(metricDAO.save(any(MetricPO.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContext.getContext().clear();
    }

    @Test
    @DisplayName("create copies immutable subject datasource and initializes revision")
    void createCopiesSubjectDatasource() {
        Metric metric = validMetric();
        metric.setDataSourceId("attacker-controlled");

        Metric created = metricService.createMetric("subject-1", metric);

        assertThat(created.getDataSourceId()).isEqualTo("ds-1");
        assertThat(created.getSubjectId()).isEqualTo("subject-1");
        assertThat(created.getRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("create rejects duplicate code in same subject")
    void createRejectsDuplicateCode() {
        when(metricDAO.findBySubjectIdAndCode("subject-1", "quality_daily_fpy"))
            .thenReturn(Optional.of(new MetricPO()));

        assertThatThrownBy(() -> metricService.createMetric("subject-1", validMetric()))
            .isInstanceOf(DatiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.SM_METRIC_CODE_EXISTS);
    }

    @Test
    @DisplayName("create rejects unstable or non-snake-case metric code")
    void createRejectsInvalidCode() {
        Metric metric = validMetric();
        metric.setCode("Quality-FPY");

        assertThatThrownBy(() -> metricService.createMetric("subject-1", metric))
            .isInstanceOf(DatiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.SM_METRIC_CODE_INVALID);
    }

    @Test
    @DisplayName("update applies mutable fields and increments revision")
    void updateIncrementsRevision() {
        MetricPO existing = new MetricPO();
        existing.setId("metric-1");
        existing.setSubjectId("subject-1");
        existing.setDataSourceId("ds-1");
        existing.setCode("quality_daily_fpy");
        existing.setSqlTemplate("SELECT 1");
        existing.setTimeout(30);
        existing.setMaxRows(1000);
        existing.setRevision(3);
        when(metricDAO.findById("metric-1")).thenReturn(Optional.of(existing));

        Metric patch = new Metric();
        patch.setName("质量日报 FPY v2");
        patch.setSqlTemplate("SELECT 2");

        Metric updated = metricService.updateMetric("metric-1", patch);

        assertThat(updated.getName()).isEqualTo("质量日报 FPY v2");
        assertThat(updated.getSqlTemplate()).isEqualTo("SELECT 2");
        assertThat(updated.getRevision()).isEqualTo(4);
        assertThat(updated.getDataSourceId()).isEqualTo("ds-1");
        assertThat(updated.getCode()).isEqualTo("quality_daily_fpy");
    }

    @Test
    @DisplayName("budget outside allowed range is rejected")
    void rejectsInvalidBudget() {
        Metric metric = validMetric();
        metric.setTimeout(301);

        assertThatThrownBy(() -> metricService.createMetric("subject-1", metric))
            .isInstanceOf(DatiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PARAMETER);
    }

    private Metric validMetric() {
        Metric metric = new Metric();
        metric.setCode("quality_daily_fpy");
        metric.setName("质量日报 FPY");
        metric.setSqlTemplate("SELECT * FROM quality_daily WHERE date >= {{start_date}}");
        metric.setTimeout(30);
        metric.setMaxRows(1000);
        return metric;
    }
}
