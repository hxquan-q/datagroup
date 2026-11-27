package com.dati.system.server.controller;

import com.dati.system.domain.service.DashboardService;
import com.dati.system.server.pojo.DashboardOverviewVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public DashboardOverviewVO getOverview(@RequestParam(defaultValue = "15") int days) {
        return dashboardService.getOverview(days);
    }
}
