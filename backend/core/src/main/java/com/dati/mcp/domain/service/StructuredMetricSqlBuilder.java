package com.dati.mcp.domain.service;

import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.ToolError;
import com.dati.semantic.domain.model.MetricDimension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class StructuredMetricSqlBuilder {

    private StructuredMetricSqlBuilder() {
    }

    static boolean isStructured(McpServiceSnapshot.MetricDraft metric) {
        return metric.measureExpression() != null && !metric.measureExpression().isBlank()
            && metric.factTableName() != null && !metric.factTableName().isBlank();
    }

    static String build(McpServiceSnapshot.MetricDraft metric, List<String> requestedDimensions,
                        List<McpServiceSnapshot.TableRelationDraft> relations, Map<String, Object> arguments) {
        if (metric.measureExpression().contains(";") || metric.measureExpression().contains("--")
            || metric.measureExpression().contains("/*") || metric.measureExpression().contains("*/")) {
            throw new ToolExecuteException(ToolError.PARAM_INVALID, "measureExpression must be a single SQL expression");
        }
        Map<String, MetricDimension> mappings = new LinkedHashMap<>();
        for (MetricDimension dimension : metric.dimensionMappings()) {
            if (dimension != null && dimension.getName() != null) {
                mappings.put(dimension.getName(), dimension);
            }
        }

        Set<String> activeDimensionNames = new LinkedHashSet<>();
        for (String dimension : requestedDimensions) {
            MetricDimension mapping = mappings.get(dimension);
            if (mapping == null) {
                throw new ToolExecuteException(ToolError.PARAM_INVALID,
                    "No structured table/column mapping for dimension " + dimension + " in metric " + metric.code());
            }
            activeDimensionNames.add(dimension);
        }
        for (Map.Entry<String, MetricDimension> entry : mappings.entrySet()) {
            Object filter = arguments.get(entry.getKey());
            if (filter != null && !(filter instanceof String text && text.isEmpty())) {
                activeDimensionNames.add(entry.getKey());
            }
        }

        Endpoint fact = new Endpoint(metric.dataSourceId(), metric.factSchema(), metric.factTableName());
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put(fact.key(), "f");
        List<String> joins = new ArrayList<>();
        Map<String, String> dimensionAliases = new LinkedHashMap<>();
        int joinIndex = 1;
        for (String dimensionName : activeDimensionNames) {
            MetricDimension dimension = mappings.get(dimensionName);
            validateIdentifier(dimension.getSchema(), "dimension schema");
            validateIdentifier(dimension.getTableName(), "dimension table");
            validateIdentifier(dimension.getColumn(), "dimension column");
            String schema = dimension.getSchema() == null || dimension.getSchema().isBlank()
                ? metric.factSchema() : dimension.getSchema();
            Endpoint endpoint = new Endpoint(metric.dataSourceId(), schema, dimension.getTableName());
            String key = endpoint.key();
            String alias = aliases.get(key);
            if (alias == null) {
                alias = "d" + joinIndex++;
                aliases.put(key, alias);
                joins.add(buildJoin(metric, fact, endpoint, alias, relations));
            }
            dimensionAliases.put(dimensionName, alias);
        }

        List<String> selectedDimensions = requestedDimensions.stream().distinct().toList();
        StringBuilder sql = new StringBuilder("SELECT ");
        if (selectedDimensions.isEmpty()) {
            sql.append(metric.measureExpression()).append(" AS metric_value");
        } else {
            for (int i = 0; i < selectedDimensions.size(); i++) {
                if (i > 0) sql.append(", ");
                String name = selectedDimensions.get(i);
                validateIdentifier(name, "dimension name");
                MetricDimension dimension = mappings.get(name);
                sql.append(dimensionAliases.get(name)).append('.').append(dimension.getColumn())
                    .append(" AS ").append(name);
            }
            sql.append(", ").append(metric.measureExpression()).append(" AS metric_value");
        }
        sql.append("\nFROM ").append(qualified(metric.factSchema(), metric.factTableName())).append(" f");
        for (String join : joins) {
            sql.append('\n').append(join);
        }

        boolean hasTimeFilter = metric.timeField() != null && !metric.timeField().isBlank();
        boolean hasDimensionFilter = mappings.keySet().stream().anyMatch(name -> arguments.get(name) != null);
        if (hasTimeFilter || hasDimensionFilter) {
            validateIdentifier(metric.timeField(), "time field");
            sql.append("\n{{#where}}\n");
            if (hasTimeFilter) {
                sql.append("{{#if start_time}}AND f.").append(metric.timeField()).append(" >= {{start_time}}{{/if}}\n");
                sql.append("{{#if end_time}}AND f.").append(metric.timeField()).append(" <= {{end_time}}{{/if}}\n");
            }
            for (Map.Entry<String, MetricDimension> entry : mappings.entrySet()) {
                if (arguments.get(entry.getKey()) == null) continue;
                String alias = dimensionAliases.get(entry.getKey());
                if (alias == null) continue;
                sql.append("{{#if ").append(entry.getKey()).append("}}AND ").append(alias).append('.')
                    .append(entry.getValue().getColumn()).append(" IN ({{").append(entry.getKey()).append("}}){{/if}}\n");
            }
            sql.append("{{/where}}");
        }
        if (!selectedDimensions.isEmpty()) {
            sql.append("\nGROUP BY ");
            for (int i = 0; i < selectedDimensions.size(); i++) {
                if (i > 0) sql.append(", ");
                String name = selectedDimensions.get(i);
                sql.append(dimensionAliases.get(name)).append('.').append(mappings.get(name).getColumn());
            }
        }
        return sql.toString();
    }

    private static String buildJoin(McpServiceSnapshot.MetricDraft metric, Endpoint fact, Endpoint dimension,
                                    String dimensionAlias, List<McpServiceSnapshot.TableRelationDraft> relations) {
        for (McpServiceSnapshot.TableRelationDraft relation : relations) {
            Endpoint relationFact = new Endpoint(relation.factDataSourceId(), relation.factSchema(), relation.factTableName());
            Endpoint relationDimension = new Endpoint(relation.dimensionDataSourceId(), relation.dimensionSchema(), relation.dimensionTableName());
            if (fact.matches(relationFact) && dimension.matches(relationDimension)) {
                validateIdentifier(relation.factColumn(), "fact join column");
                validateIdentifier(relation.dimensionColumn(), "dimension join column");
                return "LEFT JOIN " + qualified(dimension.schema(), dimension.tableName()) + " " + dimensionAlias
                    + " ON f." + relation.factColumn() + " = " + dimensionAlias + "." + relation.dimensionColumn();
            }
            if (fact.matches(relationDimension) && dimension.matches(relationFact)) {
                validateIdentifier(relation.factColumn(), "fact join column");
                validateIdentifier(relation.dimensionColumn(), "dimension join column");
                return "LEFT JOIN " + qualified(dimension.schema(), dimension.tableName()) + " " + dimensionAlias
                    + " ON f." + relation.dimensionColumn() + " = " + dimensionAlias + "." + relation.factColumn();
            }
        }
        throw new ToolExecuteException(ToolError.PARAM_INVALID,
            "No confirmed one-hop table relation from " + metric.factTableName() + " to " + dimension.tableName()
                + " for metric " + metric.code());
    }

    private static String qualified(String schema, String table) {
        validateIdentifier(schema, "schema");
        validateIdentifier(table, "table");
        return schema == null || schema.isBlank() ? table : schema + "." + table;
    }

    private static void validateIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!value.matches("[\\p{L}_][\\p{L}\\p{N}_]*")) {
            throw new ToolExecuteException(ToolError.PARAM_INVALID, label + " must be an unquoted SQL identifier");
        }
    }

    private record Endpoint(String dataSourceId, String schema, String tableName) {
        String key() {
            return lower(schema) + "\u0000" + lower(tableName);
        }

        boolean matches(Endpoint other) {
            return Objects.equals(dataSourceId, other.dataSourceId)
                && Objects.equals(lower(schema), lower(other.schema))
                && Objects.equals(lower(tableName), lower(other.tableName));
        }

        private static String lower(String value) {
            return value == null ? null : value.toLowerCase(Locale.ROOT);
        }
    }
}
