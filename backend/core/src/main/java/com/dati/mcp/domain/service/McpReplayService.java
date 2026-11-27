package com.dati.mcp.domain.service;

import com.dati.base.exception.DatiException;
import com.dati.base.exception.ErrorCode;
import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.repository.dao.McpInvocationLogDAO;
import com.dati.mcp.repository.dao.McpServiceDAO;
import com.dati.mcp.repository.dao.McpServiceSnapshotDAO;
import com.dati.mcp.repository.mapper.McpServiceSnapshotMapper;
import com.dati.mcp.repository.po.McpInvocationLogPO;
import com.dati.mcp.repository.po.McpServicePO;
import com.dati.mcp.repository.po.McpServiceSnapshotPO;
import com.dati.mcp.server.endpoint.McpProtocolHandler;
import com.dati.mcp.server.pojo.McpReplayResultVO;
import com.dati.permission.domain.service.PermissionService;
import com.dati.permission.domain.model.Permission;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Replays a logged MCP tools/call invocation against the current active snapshot. */
@Service
public class McpReplayService {

    private final McpInvocationLogDAO logDAO;
    private final McpServiceDAO serviceDAO;
    private final McpServiceSnapshotDAO snapshotDAO;
    private final McpProtocolHandler protocolHandler;
    private final PermissionService permissionService;

    private static final java.util.Set<String> WRITE_TOOLS = java.util.Set.of(
        "upsert_business_term", "update_table_info", "update_column_info");

    public McpReplayService(McpInvocationLogDAO logDAO,
                            McpServiceDAO serviceDAO,
                            McpServiceSnapshotDAO snapshotDAO,
                            McpProtocolHandler protocolHandler,
                            PermissionService permissionService) {
        this.logDAO = logDAO;
        this.serviceDAO = serviceDAO;
        this.snapshotDAO = snapshotDAO;
        this.protocolHandler = protocolHandler;
        this.permissionService = permissionService;
    }

    @SuppressWarnings("unchecked")
    public McpReplayResultVO replay(String serviceId, String logId) {
        permissionService.requireMcpService(serviceId, Permission.EDIT);
        McpInvocationLogPO log = logDAO.findById(logId)
            .filter(entry -> serviceId.equals(entry.getServiceId()))
            .orElseThrow(() -> new DatiException(ErrorCode.NOT_FOUND));
        if (!McpSchema.METHOD_TOOLS_CALL.equals(log.getMethod()) || log.getToolName() == null || log.getToolName().isBlank()) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "Only tools/call invocations can be replayed");
        }
        if (log.getRequestParams() == null || log.getRequestParams().isBlank()) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "Invocation has no recorded parameters; cannot replay");
        }
        McpServicePO service = serviceDAO.findById(serviceId)
            .orElseThrow(() -> new DatiException(ErrorCode.NOT_FOUND));
        McpServiceSnapshot.SnapshotContent content = loadActiveSnapshot(service);
        if (content == null) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "Service has no active published version");
        }

        if (WRITE_TOOLS.contains(log.getToolName())) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER,
                "Write tools cannot be replayed: " + log.getToolName());
        }
        Map<String, Object> arguments;
        try {
            arguments = com.dati.common.JsonUtils.fromJson(log.getRequestParams(), Map.class);
        } catch (Exception e) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "Recorded parameters are not valid JSON");
        }
        String sqlProbe = log.getExecutedSql();
        if (sqlProbe == null && arguments.get("sql") instanceof String paramSql) {
            sqlProbe = paramSql;
        }
        if (sqlProbe != null && !isReadOnlySql(sqlProbe)) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER,
                "Only read-only invocations can be replayed");
        }

        McpReplayResultVO vo = new McpReplayResultVO();
        vo.setLogId(log.getId());
        vo.setToolName(log.getToolName());
        vo.setOriginalSuccess(log.getSuccess());
        vo.setOriginalRowCount(log.getRowCount());
        vo.setOriginalDurationMs(log.getDurationMs());

        McpSchema.JSONRPCRequest req = new McpSchema.JSONRPCRequest(
            McpSchema.METHOD_TOOLS_CALL, 1,
            Map.of("name", log.getToolName(), "arguments", arguments));
        long start = System.currentTimeMillis();
        McpSchema.JSONRPCResponse resp = protocolHandler.handle(service, content, req);
        vo.setReplayDurationMs(System.currentTimeMillis() - start);

        boolean success = true;
        String error = null;
        String executedSql = null;
        Integer rowCount = null;
        if (resp.error() != null) {
            success = false;
            error = resp.error().message();
        } else if (resp.result() instanceof McpSchema.CallToolResult callResult) {
            if (Boolean.TRUE.equals(callResult.isError())) {
                success = false;
                error = "Tool error";
            } else if (callResult.structuredContent() instanceof Map<?, ?> structuredMap) {
                Map<String, Object> structured = (Map<String, Object>) structuredMap;
                executedSql = evidenceSql(structured);
                rowCount = evidenceRowCount(structured);
            }
        }
        vo.setReplaySuccess(success);
        vo.setReplayError(error);
        vo.setReplayExecutedSql(executedSql);
        vo.setReplayRowCount(rowCount);
        vo.setRowCountMatch(log.getRowCount() != null && rowCount != null
            && Objects.equals(log.getRowCount(), rowCount));
        vo.setSqlChanged(log.getExecutedSql() != null && executedSql != null
            && !log.getExecutedSql().equals(executedSql));
        return vo;
    }

    private static final java.util.regex.Pattern WRITE_KEYWORD = java.util.regex.Pattern.compile(
        "\\b(insert|update|delete|merge)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Every statement must start with a read-only keyword, and no data-modifying CTE or subquery
     * write (insert/update/delete/merge) may appear anywhere. Literals, quoted identifiers, and
     * comments are stripped before scanning, so read-only SQL mentioning those words still replays.
     */
    static boolean isReadOnlySql(String sql) {
        String sanitized = sanitize(sql);
        for (String statement : sanitized.split(";")) {
            String normalized = statement.stripLeading().toLowerCase(java.util.Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            boolean readOnly = normalized.startsWith("select") || normalized.startsWith("with")
                || normalized.startsWith("explain") || normalized.startsWith("show")
                || normalized.startsWith("describe") || normalized.startsWith("desc");
            if (!readOnly || WRITE_KEYWORD.matcher(normalized).find()) {
                return false;
            }
        }
        return true;
    }

    /** Removes line/block comments and single/double/backtick-quoted literals. */
    private static String sanitize(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int newline = sql.indexOf('\n', i);
                i = newline < 0 ? n : newline + 1;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                i++;
                while (i < n) {
                    if (sql.charAt(i) == quote) {
                        if (i + 1 < n && sql.charAt(i + 1) == quote) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }


    private McpServiceSnapshot.SnapshotContent loadActiveSnapshot(McpServicePO service) {
        if (service.getActiveVersionId() == null) {
            return null;
        }
        Optional<McpServiceSnapshotPO> po = snapshotDAO.findById(service.getActiveVersionId());
        return po.map(snapshot -> McpServiceSnapshotMapper.toModel(snapshot).getContent()).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private String evidenceSql(Map<String, Object> structured) {
        Object sql = structured.get("executed_sql");
        if (sql instanceof String text) {
            return text;
        }
        Object execution = structured.get("execution");
        if (execution instanceof Map<?, ?> executionMap) {
            Object nested = ((Map<String, Object>) executionMap).get("executed_sql");
            return nested instanceof String text ? text : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Integer evidenceRowCount(Map<String, Object> structured) {
        Object execution = structured.containsKey("execution") ? structured.get("execution") : structured;
        if (!(execution instanceof Map<?, ?> executionMap)) {
            return null;
        }
        Object results = ((Map<String, Object>) executionMap).get("results");
        if (!(results instanceof List<?> rows)) {
            return null;
        }
        int total = 0;
        boolean counted = false;
        for (Object item : rows) {
            if (item instanceof Map<?, ?> itemMap) {
                Object count = ((Map<String, Object>) itemMap).get("row_count");
                if (!(count instanceof Number)) {
                    count = ((Map<String, Object>) itemMap).get("returned_rows");
                }
                if (count instanceof Number number) {
                    total += number.intValue();
                    counted = true;
                } else {
                    Object rowsData = ((Map<String, Object>) itemMap).get("rows");
                    if (rowsData instanceof List<?> rowList) {
                        total += rowList.size();
                        counted = true;
                    }
                }
            }
        }
        return counted ? total : null;
    }
}
