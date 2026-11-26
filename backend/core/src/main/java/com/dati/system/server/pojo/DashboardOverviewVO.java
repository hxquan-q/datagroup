package com.dati.system.server.pojo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardOverviewVO {
    private ResourceOverviewVO dataSources;
    private ResourceOverviewVO subjects;
    private ResourceOverviewVO mcpServices;

    @Data
    @Builder
    public static class ResourceOverviewVO {
        private long total;
        private List<DailyCountVO> dailyCreated;
    }

    @Data
    @Builder
    public static class DailyCountVO {
        private String date;
        private long count;
    }
}
