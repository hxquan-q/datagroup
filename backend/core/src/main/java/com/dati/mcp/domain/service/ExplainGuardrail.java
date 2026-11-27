package com.dati.mcp.domain.service;

import com.dati.common.JsonUtils;
import com.dati.db.DbType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Execution-plan guardrail for read-only queries: rejects full table/index scans whose
 * estimated rows exceed the configured threshold, before the query itself runs.
 */
public final class ExplainGuardrail {

    private static final Set<DbType> MYSQL_FAMILY = Set.of(DbType.MYSQL, DbType.MARIADB, DbType.DORIS);

    private ExplainGuardrail() {
    }

    public static boolean supports(DbType type) {
        return type != null && MYSQL_FAMILY.contains(type);
    }

    public record Offender(String table, String accessType, long estimatedRows, List<String> possibleKeys) {
    }

    /** Walks a MySQL EXPLAIN FORMAT=JSON plan tree and collects full-scan offenders. */
    public static List<Offender> inspectMySqlPlan(String planJson, long maxExplainRows) {
        List<Offender> offenders = new ArrayList<>();
        JsonNode root;
        try {
            root = JsonUtils.parseJson(planJson);
        } catch (Exception e) {
            return offenders;
        }
        visit(root, maxExplainRows, offenders);
        return offenders;
    }

    private static void visit(JsonNode node, long maxExplainRows, List<Offender> offenders) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> visit(child, maxExplainRows, offenders));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        JsonNode table = node.get("table");
        if (table != null && table.isObject()) {
            inspectTable(table, maxExplainRows, offenders);
        }
        node.fields().forEachRemaining(entry -> {
            if (!"table".equals(entry.getKey())) {
                visit(entry.getValue(), maxExplainRows, offenders);
            }
        });
    }

    private static void inspectTable(JsonNode table, long maxExplainRows, List<Offender> offenders) {
        JsonNode nameNode = table.get("table_name");
        if (nameNode == null || !nameNode.isTextual()) {
            return;
        }
        String tableName = nameNode.asText();
        if (tableName.startsWith("<")) {
            return;
        }
        String accessType = table.path("access_type").asText("");
        if (!"ALL".equals(accessType) && !"index".equals(accessType)) {
            return;
        }
        long estimatedRows = table.path("rows_examined_per_scan").asLong(table.path("rows").asLong(0));
        if (estimatedRows <= maxExplainRows) {
            return;
        }
        List<String> possibleKeys = new ArrayList<>();
        JsonNode keys = table.get("possible_keys");
        if (keys != null && keys.isArray()) {
            keys.forEach(key -> {
                if (key.isTextual()) {
                    possibleKeys.add(key.asText());
                }
            });
        }
        offenders.add(new Offender(tableName, accessType, estimatedRows, List.copyOf(possibleKeys)));
    }

    /** Builds the rejection message with offender details and rewrite hints. */
    public static String rejectionMessage(List<Offender> offenders, long maxExplainRows) {
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < offenders.size(); i++) {
            Offender offender = offenders.get(i);
            if (i > 0) {
                summary.append("; ");
            }
            summary.append(offender.table())
                .append(" (type=").append(offender.accessType())
                .append(", rows≈").append(offender.estimatedRows());
            if (offender.possibleKeys().isEmpty()) {
                summary.append(", keys=none");
            } else {
                summary.append(", keys=[").append(String.join(", ", offender.possibleKeys())).append("]");
            }
            summary.append(")");
        }
        return "Query rejected by execution-plan guardrail: full table/index scan estimated over "
            + maxExplainRows + " rows — " + summary
            + ". Add indexed WHERE filters (time range or key columns) to narrow the scan,"
            + " or use get_sample_data to preview rows.";
    }
}
