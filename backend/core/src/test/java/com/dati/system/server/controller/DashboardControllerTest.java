package com.dati.system.server.controller;

import com.dati.system.domain.service.DashboardService;
import com.dati.system.server.pojo.DashboardOverviewVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("DashboardController unit tests")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    @DisplayName("Overview returns resource totals and daily creation trends")
    void getOverview_shouldReturnDashboardOverview() throws Exception {
        DashboardOverviewVO.ResourceOverviewVO resource = DashboardOverviewVO.ResourceOverviewVO.builder()
                .total(3)
                .dailyCreated(List.of(DashboardOverviewVO.DailyCountVO.builder()
                        .date("2026-11-26")
                        .count(2)
                        .build()))
                .build();
        when(dashboardService.getOverview(15)).thenReturn(DashboardOverviewVO.builder()
                .dataSources(resource)
                .subjects(resource)
                .mcpServices(resource)
                .build());

        mockMvc.perform(get("/v1/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data_sources.total").value(3))
                .andExpect(jsonPath("$.data_sources.daily_created[0].date").value("2026-11-26"))
                .andExpect(jsonPath("$.data_sources.daily_created[0].count").value(2))
                .andExpect(jsonPath("$.subjects.total").value(3))
                .andExpect(jsonPath("$.mcp_services.total").value(3));

        verify(dashboardService).getOverview(15);
    }

    @Test
    @DisplayName("Overview passes requested day window")
    void getOverview_shouldPassRequestedDays() throws Exception {
        DashboardOverviewVO.ResourceOverviewVO resource = DashboardOverviewVO.ResourceOverviewVO.builder()
                .total(0)
                .dailyCreated(List.of())
                .build();
        when(dashboardService.getOverview(30)).thenReturn(DashboardOverviewVO.builder()
                .dataSources(resource)
                .subjects(resource)
                .mcpServices(resource)
                .build());

        mockMvc.perform(get("/v1/dashboard/overview").param("days", "30"))
                .andExpect(status().isOk());

        verify(dashboardService).getOverview(30);
    }
}
