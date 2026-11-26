package com.dati.semantic.domain.service;

import com.dati.TestFixtures;
import com.dati.auth.authentication.User;
import com.dati.base.RequestContext;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.model.ValidationCase;
import com.dati.semantic.repository.dao.MetricDAO;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.dao.ValidationCaseDAO;
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
}
