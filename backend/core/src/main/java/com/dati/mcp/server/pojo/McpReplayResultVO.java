package com.dati.mcp.server.pojo;

import lombok.Data;

@Data
public class McpReplayResultVO {

    private String logId;

    private String toolName;

    private Boolean originalSuccess;

    private Integer originalRowCount;

    private Long originalDurationMs;

    private Boolean replaySuccess;

    private Integer replayRowCount;

    private Long replayDurationMs;

    private String replayError;

    private String replayExecutedSql;

    private Boolean rowCountMatch;

    private Boolean sqlChanged;
}
