package com.dati.system.domain.service;

import com.dati.auth.authentication.User;
import com.dati.auth.domain.service.UserGroupService;
import com.dati.base.RequestContext;
import com.dati.datasource.repository.dao.DataSourceDAO;
import com.dati.mcp.repository.dao.McpServiceDAO;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.system.server.pojo.DashboardOverviewVO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final int DEFAULT_DAYS = 15;
    private static final int MAX_DAYS = 90;

    private final DataSourceDAO dataSourceDAO;
    private final SubjectDAO subjectDAO;
    private final McpServiceDAO mcpServiceDAO;
    private final PermissionService permissionService;
    private final UserGroupService userGroupService;

    public DashboardService(DataSourceDAO dataSourceDAO,
                            SubjectDAO subjectDAO,
                            McpServiceDAO mcpServiceDAO,
                            PermissionService permissionService,
                            UserGroupService userGroupService) {
        this.dataSourceDAO = dataSourceDAO;
        this.subjectDAO = subjectDAO;
        this.mcpServiceDAO = mcpServiceDAO;
        this.permissionService = permissionService;
        this.userGroupService = userGroupService;
    }

    public DashboardOverviewVO getOverview(int days) {
        int queryDays = normalizeDays(days);
        User user = RequestContext.getUser();
        boolean admin = permissionService.isAdmin(user.getName());
        Collection<String> groupIds = userGroupService.groupIdsOf(user.getId());

        List<Instant> dataSources = admin
                ? dataSourceDAO.findAllCreatedAt()
                : dataSourceDAO.findAccessibleCreatedAt(user.getId(), groupIds);
        List<Instant> subjects = admin
                ? subjectDAO.findAllCreatedAt()
                : subjectDAO.findAccessibleCreatedAt(user.getId(), groupIds);
        List<Instant> mcpServices = admin
                ? mcpServiceDAO.findAllCreatedAt()
                : mcpServiceDAO.findAccessibleCreatedAt(user.getId(), groupIds);

        return DashboardOverviewVO.builder()
                .dataSources(summarize(dataSources, queryDays))
                .subjects(summarize(subjects, queryDays))
                .mcpServices(summarize(mcpServices, queryDays))
                .build();
    }

    private int normalizeDays(int days) {
        if (days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private DashboardOverviewVO.ResourceOverviewVO summarize(List<Instant> createdAtValues, int days) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate firstDay = today.minusDays(days - 1L);
        Map<LocalDate, Long> countsByDate = createdAtValues.stream()
                .filter(Objects::nonNull)
                .map(instant -> instant.atZone(zone).toLocalDate())
                .filter(date -> !date.isBefore(firstDay) && !date.isAfter(today))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<DashboardOverviewVO.DailyCountVO> dailyCreated = firstDay.datesUntil(today.plusDays(1))
                .map(date -> DashboardOverviewVO.DailyCountVO.builder()
                        .date(date.toString())
                        .count(countsByDate.getOrDefault(date, 0L))
                        .build())
                .toList();

        return DashboardOverviewVO.ResourceOverviewVO.builder()
                .total(createdAtValues.size())
                .dailyCreated(dailyCreated)
                .build();
    }
}
