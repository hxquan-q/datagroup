package com.dati.system.domain.service;

import com.dati.TestFixtures;
import com.dati.auth.authentication.User;
import com.dati.auth.domain.service.UserGroupService;
import com.dati.base.RequestContext;
import com.dati.datasource.repository.dao.DataSourceDAO;
import com.dati.mcp.repository.dao.McpServiceDAO;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.system.server.pojo.DashboardOverviewVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService unit tests")
class DashboardServiceTest {

    @Mock
    private DataSourceDAO dataSourceDAO;

    @Mock
    private SubjectDAO subjectDAO;

    @Mock
    private McpServiceDAO mcpServiceDAO;

    @Mock
    private PermissionService permissionService;

    @Mock
    private UserGroupService userGroupService;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(TestFixtures.TEST_USER_ID);
        user.setName("test-user");
        RequestContext.setUser(user);
        lenient().when(permissionService.isAdmin(anyString())).thenReturn(false);
        lenient().when(userGroupService.groupIdsOf(TestFixtures.TEST_USER_ID)).thenReturn(Set.of("ALL_USERS"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("Overview totals count all accessible resources and trends count creations by day")
    void getOverview_shouldSummarizeAccessibleCreationTrends() {
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant outsideWindow = now.minus(30, ChronoUnit.DAYS);
        when(dataSourceDAO.findAccessibleCreatedAt(anyString(), any())).thenReturn(List.of(now, now, outsideWindow));
        when(subjectDAO.findAccessibleCreatedAt(anyString(), any())).thenReturn(List.of(yesterday));
        when(mcpServiceDAO.findAccessibleCreatedAt(anyString(), any())).thenReturn(List.of());

        DashboardOverviewVO overview = dashboardService.getOverview(15);

        assertThat(overview.getDataSources().getTotal()).isEqualTo(3);
        assertThat(overview.getSubjects().getTotal()).isEqualTo(1);
        assertThat(overview.getMcpServices().getTotal()).isZero();
        assertThat(overview.getDataSources().getDailyCreated()).hasSize(15);

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        long todayCount = overview.getDataSources().getDailyCreated().stream()
                .filter(item -> item.getDate().equals(today.toString()))
                .findFirst()
                .orElseThrow()
                .getCount();
        assertThat(todayCount).isEqualTo(2);
        assertThat(overview.getDataSources().getDailyCreated().stream().mapToLong(DashboardOverviewVO.DailyCountVO::getCount).sum())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Overview uses unrestricted queries for admins")
    void getOverview_shouldUseUnrestrictedQueriesForAdmin() {
        when(permissionService.isAdmin("test-user")).thenReturn(true);
        when(dataSourceDAO.findAllCreatedAt()).thenReturn(List.of());
        when(subjectDAO.findAllCreatedAt()).thenReturn(List.of());
        when(mcpServiceDAO.findAllCreatedAt()).thenReturn(List.of());

        dashboardService.getOverview(15);

        verify(dataSourceDAO).findAllCreatedAt();
        verify(subjectDAO).findAllCreatedAt();
        verify(mcpServiceDAO).findAllCreatedAt();
        verify(dataSourceDAO, never()).findAccessibleCreatedAt(anyString(), any());
    }

    @Test
    @DisplayName("Overview honors requested day windows")
    void getOverview_shouldHonorRequestedDayWindow() {
        when(dataSourceDAO.findAccessibleCreatedAt(anyString(), any())).thenReturn(List.of());
        when(subjectDAO.findAccessibleCreatedAt(anyString(), any())).thenReturn(List.of());
        when(mcpServiceDAO.findAccessibleCreatedAt(anyString(), any())).thenReturn(List.of());

        DashboardOverviewVO overview = dashboardService.getOverview(7);

        assertThat(overview.getDataSources().getDailyCreated()).hasSize(7);
    }
}
