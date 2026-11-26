package com.dati.semantic.domain.service;

import com.dati.base.exception.DatiException;
import com.dati.base.exception.ErrorCode;
import com.dati.datasource.repository.dao.ColumnInfoDAO;
import com.dati.datasource.repository.dao.DataSourceDAO;
import com.dati.datasource.repository.dao.TableForeignKeyDAO;
import com.dati.datasource.repository.po.ColumnInfoPO;
import com.dati.datasource.repository.po.DataSourcePO;
import com.dati.datasource.repository.po.TableForeignKeyPO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.db.DbType;
import com.dati.permission.domain.model.Permission;
import com.dati.permission.domain.model.ResourceType;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.SemanticEntityType;
import com.dati.semantic.domain.model.TableRelationRecommendation;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.dao.SubjectTableDAO;
import com.dati.semantic.repository.dao.TableRelationDAO;
import com.dati.semantic.repository.po.SemanticSearchDocument;
import com.dati.semantic.repository.po.SubjectPO;
import com.dati.semantic.repository.po.TableRelationPO;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TableRelationRecommendationService {
    private static final int MAX_TABLES = 200;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MIN_SCORE = 60;

    private final SubjectDAO subjectDAO;
    private final SubjectTableDAO subjectTableDAO;
    private final TableRelationDAO tableRelationDAO;
    private final ColumnInfoDAO columnInfoDAO;
    private final TableForeignKeyDAO tableForeignKeyDAO;
    private final SemanticIndexService semanticIndexService;
    private final DataSourceDAO dataSourceDAO;
    private final PermissionService permissionService;

    public TableRelationRecommendationService(SubjectDAO subjectDAO, SubjectTableDAO subjectTableDAO,
                                              TableRelationDAO tableRelationDAO, ColumnInfoDAO columnInfoDAO, TableForeignKeyDAO tableForeignKeyDAO,
                                              SemanticIndexService semanticIndexService, DataSourceDAO dataSourceDAO,
                                              PermissionService permissionService) {
        this.subjectDAO = subjectDAO;
        this.subjectTableDAO = subjectTableDAO;
        this.tableRelationDAO = tableRelationDAO;
        this.columnInfoDAO = columnInfoDAO;
        this.tableForeignKeyDAO = tableForeignKeyDAO;
        this.semanticIndexService = semanticIndexService;
        this.dataSourceDAO = dataSourceDAO;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public List<TableRelationRecommendation> recommendRelations(String subjectId, Integer limit) {
        SubjectPO subject = requireSubject(subjectId);
        permissionService.requireCurrentUser(ResourceType.SUBJECT, subjectId, Permission.EDIT, subject.getCreatedBy());
        boolean canUseDictionaryValues = canUseDictionaryValues(subject);
        boolean caseInsensitiveColumns = usesCaseInsensitiveColumns(subject);
        List<TableInfoPO> tables = subjectTableDAO.findTablesBySubjectId(subjectId, PageRequest.of(0, MAX_TABLES)).getContent();
        if (tables.size() < 2) return List.of();

        Map<String, List<ColumnInfoPO>> columnsByTable = new HashMap<>();
        for (TableInfoPO table : tables) {
            columnsByTable.put(table.getId(), columnInfoDAO.findByTableId(table.getId()));
        }
        Set<String> existing = new HashSet<>();
        for (TableRelationPO relation : tableRelationDAO.findBySubjectId(subjectId)) {
            existing.add(relationKey(relation.getFactTableId(), relation.getFactColumn(), relation.getDimensionTableId(), relation.getDimensionColumn(), caseInsensitiveColumns));
        }

        Map<String, Set<String>> valueCache = new HashMap<>();
        List<TableRelationRecommendation> candidates = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        for (TableRelationRecommendation physical : physicalCandidates(tables, columnsByTable, caseInsensitiveColumns)) {
            String key = relationKey(physical.getFactTableId(), physical.getFactColumn(), physical.getDimensionTableId(), physical.getDimensionColumn(), caseInsensitiveColumns);
            if (existing.contains(key) || emitted.contains(key)) continue;
            emitted.add(key);
            candidates.add(physical);
        }
        for (TableInfoPO factTable : tables) {
            for (TableInfoPO dimensionTable : tables) {
                if (factTable.getId().equals(dimensionTable.getId())) continue;
                for (ColumnInfoPO factColumn : columnsByTable.getOrDefault(factTable.getId(), List.of())) {
                    for (ColumnInfoPO dimensionColumn : columnsByTable.getOrDefault(dimensionTable.getId(), List.of())) {
                        TableRelationRecommendation candidate = evaluate(factTable, factColumn, dimensionTable, dimensionColumn, valueCache, canUseDictionaryValues);
                        if (candidate == null) continue;
                        String key = relationKey(candidate.getFactTableId(), candidate.getFactColumn(), candidate.getDimensionTableId(), candidate.getDimensionColumn(), caseInsensitiveColumns);
                        if (existing.contains(key) || emitted.contains(key)) continue;
                        emitted.add(key);
                        candidates.add(candidate);
                    }
                }
            }
        }

        int resultLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 200);
        return candidates.stream()
            .sorted(Comparator.comparing(TableRelationRecommendation::getScore).reversed())
            .limit(resultLimit)
            .toList();
    }


    private List<TableRelationRecommendation> physicalCandidates(List<TableInfoPO> tables,
                                                                 Map<String, List<ColumnInfoPO>> columnsByTable,
                                                                 boolean caseInsensitiveColumns) {
        List<String> tableIds = tables.stream().map(TableInfoPO::getId).toList();
        Map<String, List<TableForeignKeyPO>> grouped = new HashMap<>();
        for (TableForeignKeyPO key : tableForeignKeyDAO.findByTableIdIn(tableIds)) {
            String group = key.getTableId() + "|" + (key.getName() == null || key.getName().isBlank() ? key.getColumnName() : key.getName());
            grouped.computeIfAbsent(group, ignored -> new ArrayList<>()).add(key);
        }
        List<TableRelationRecommendation> result = new ArrayList<>();
        for (List<TableForeignKeyPO> rows : grouped.values()) {
            if (rows.size() != 1) continue;
            TableForeignKeyPO key = rows.getFirst();
            TableInfoPO factTable = tables.stream().filter(table -> table.getId().equals(key.getTableId())).findFirst().orElse(null);
            TableInfoPO dimensionTable = resolveReferencedTable(tables, key, caseInsensitiveColumns);
            if (factTable == null || dimensionTable == null || factTable.getId().equals(dimensionTable.getId())) continue;
            ColumnInfoPO factColumn = findColumn(columnsByTable.getOrDefault(factTable.getId(), List.of()), key.getColumnName(), caseInsensitiveColumns);
            ColumnInfoPO dimensionColumn = findColumn(columnsByTable.getOrDefault(dimensionTable.getId(), List.of()), key.getRefColumn(), caseInsensitiveColumns);
            if (factColumn == null || dimensionColumn == null) continue;

            TableRelationRecommendation candidate = new TableRelationRecommendation();
            candidate.setFactTableId(factTable.getId());
            candidate.setFactTableName(factTable.getName());
            candidate.setFactColumn(factColumn.getName());
            candidate.setDimensionTableId(dimensionTable.getId());
            candidate.setDimensionTableName(dimensionTable.getName());
            candidate.setDimensionColumn(dimensionColumn.getName());
            candidate.setCardinality(factColumn.isPrimaryKey() && dimensionColumn.isPrimaryKey() ? "ONE_TO_ONE" : factColumn.isPrimaryKey() || dimensionColumn.isPrimaryKey() ? "MANY_TO_ONE" : "MANY_TO_MANY");
            candidate.setScore(100);
            candidate.setConfidence("HIGH");
            candidate.setSource("FOREIGN_KEY");
            List<String> evidence = new ArrayList<>(List.of("FOREIGN_KEY_CONSTRAINT"));
            if (typesCompatible(factColumn.getColumnType(), dimensionColumn.getColumnType())) evidence.add("TYPE_COMPATIBLE");
            candidate.setEvidence(evidence);
            result.add(candidate);
        }
        return result;
    }

    private static TableInfoPO resolveReferencedTable(List<TableInfoPO> tables, TableForeignKeyPO key, boolean caseInsensitive) {
        List<TableInfoPO> matches = tables.stream()
            .filter(table -> namesEqual(table.getName(), key.getRefTable(), false))
            .filter(table -> key.getRefSchema() == null || namesEqual(table.getSchema(), key.getRefSchema(), false))
            .toList();
        if (matches.size() == 1) return matches.getFirst();
        if (key.getRefSchema() != null) return null;
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static ColumnInfoPO findColumn(List<ColumnInfoPO> columns, String name, boolean caseInsensitive) {
        return columns.stream().filter(column -> namesEqual(column.getName(), name, caseInsensitive)).findFirst().orElse(null);
    }

    private static boolean namesEqual(String left, String right, boolean caseInsensitive) {
        if (left == null || right == null) return false;
        return caseInsensitive ? left.equalsIgnoreCase(right) : left.equals(right);
    }

    private TableRelationRecommendation evaluate(TableInfoPO factTable, ColumnInfoPO factColumn,
                                                 TableInfoPO dimensionTable, ColumnInfoPO dimensionColumn,
                                                 Map<String, Set<String>> valueCache, boolean canUseDictionaryValues) {
        if (!isKeyLike(factColumn.getName()) || !isKeyLike(dimensionColumn.getName())) return null;
        if (!typesCompatible(factColumn.getColumnType(), dimensionColumn.getColumnType())) return null;

        int score = 0;
        Set<String> evidence = new LinkedHashSet<>();
        String factName = normalizeColumn(factColumn.getName());
        String dimensionName = normalizeColumn(dimensionColumn.getName());
        String factToken = businessToken(factName);
        String dimensionTableToken = tableToken(dimensionTable.getName());

        if (!factName.isBlank() && factName.equals(dimensionName)) {
            score += 40;
            evidence.add("COLUMN_NAME_MATCH");
        }
        if (sharesAlias(factColumn, dimensionColumn)) {
            score += 30;
            evidence.add("COLUMN_ALIAS_MATCH");
        }
        boolean tableBusinessTokenMatch = !factToken.isBlank() && factToken.equals(dimensionTableToken);
        boolean tableAliasMatch = (!factToken.isBlank() && aliasesContainToken(dimensionTable.getAliases(), factToken))
            || sharesTableAlias(factColumn, dimensionTable);
        if (!tableBusinessTokenMatch && !tableAliasMatch) return null;
        if (tableBusinessTokenMatch) {
            score += 45;
            evidence.add("TABLE_BUSINESS_TOKEN_MATCH");
        } else {
            score += 35;
            evidence.add("TABLE_ALIAS_MATCH");
        }
        boolean dimensionPrimaryKey = isGenericPrimaryKey(dimensionName);
        if (dimensionPrimaryKey) {
            score += 10;
            evidence.add("DIMENSION_PRIMARY_KEY");
            if (tableBusinessTokenMatch) score += 15;
        }
        score += 10;
        evidence.add("TYPE_COMPATIBLE");
        if (sharesDescriptionTerm(factColumn, dimensionTable)) {
            score += 10;
            evidence.add("DESCRIPTION_TERM_MATCH");
        }

        double overlap = canUseDictionaryValues ? dictionaryOverlap(factColumn, dimensionColumn, valueCache) : 0;
        if (overlap >= 0.6) {
            score += 15;
            evidence.add("VALUE_OVERLAP");
        }
        if (score < MIN_SCORE) return null;
        if (!dimensionPrimaryKey && overlap < 0.6) {
            score = Math.min(score, 84);
        }

        TableRelationRecommendation candidate = new TableRelationRecommendation();
        candidate.setFactTableId(factTable.getId());
        candidate.setFactTableName(factTable.getName());
        candidate.setFactColumn(factColumn.getName());
        candidate.setDimensionTableId(dimensionTable.getId());
        candidate.setDimensionTableName(dimensionTable.getName());
        candidate.setDimensionColumn(dimensionColumn.getName());
        candidate.setCardinality(dimensionPrimaryKey ? "MANY_TO_ONE" : "MANY_TO_MANY");
        candidate.setSource("INFERRED");
        candidate.setScore(score);
        candidate.setConfidence(score >= 85 ? "HIGH" : score >= 70 ? "MEDIUM" : "LOW");
        candidate.setEvidence(List.copyOf(evidence));
        return candidate;
    }

    private double dictionaryOverlap(ColumnInfoPO factColumn, ColumnInfoPO dimensionColumn, Map<String, Set<String>> valueCache) {
        Set<String> factValues = dictionaryValues(factColumn, valueCache);
        Set<String> dimensionValues = dictionaryValues(dimensionColumn, valueCache);
        if (factValues.size() < 2 || dimensionValues.size() < 2) return 0;
        Set<String> intersection = new HashSet<>(factValues);
        intersection.retainAll(dimensionValues);
        return (double) intersection.size() / Math.min(factValues.size(), dimensionValues.size());
    }

    private Set<String> dictionaryValues(ColumnInfoPO column, Map<String, Set<String>> valueCache) {
        String key = column.getTableId() + "." + column.getName();
        return valueCache.computeIfAbsent(key, ignored -> {
            try {
                Set<String> values = new HashSet<>();
                for (SemanticSearchDocument doc : semanticIndexService.findByTableFieldAndType(column.getTableId(), column.getName(), SemanticEntityType.FIELD_VALUE)) {
                    if (doc.getKeywords() != null && !doc.getKeywords().isEmpty()) values.add(doc.getKeywords().getFirst());
                }
                return values;
            } catch (RuntimeException e) {
                return Set.of();
            }
        });
    }

    private static boolean isKeyLike(String name) {
        String normalized = normalizeColumn(name);
        return normalized.equals("id")
            || normalized.endsWith("_id")
            || normalized.endsWith("id")
            || normalized.endsWith("_key")
            || normalized.endsWith("_code")
            || normalized.endsWith("_no")
            || normalized.endsWith("_number");
    }

    private static boolean isGenericPrimaryKey(String normalized) {
        return normalized.equals("id") || normalized.equals("f_id");
    }

    private static boolean typesCompatible(String left, String right) {
        return typeFamily(left).equals(typeFamily(right));
    }

    private static String typeFamily(String type) {
        if (type == null) return "unknown";
        String value = type.toLowerCase(Locale.ROOT);
        if (value.contains("int") || value.contains("decimal") || value.contains("numeric") || value.contains("double") || value.contains("float") || value.contains("bigint")) return "number";
        if (value.contains("date") || value.contains("time")) return "time";
        if (value.contains("bit") || value.contains("bool")) return "boolean";
        if (value.contains("uuid")) return "uuid";
        return "text";
    }

    private static boolean sharesAlias(ColumnInfoPO factColumn, ColumnInfoPO dimensionColumn) {
        Set<String> dimensionAliases = normalizedAliases(dimensionColumn.getAliases());
        return normalizedAliases(factColumn.getAliases()).stream().anyMatch(dimensionAliases::contains);
    }

    private static boolean sharesTableAlias(ColumnInfoPO factColumn, TableInfoPO dimensionTable) {
        Set<String> tableAliases = normalizedAliases(dimensionTable.getAliases());
        return normalizedAliases(factColumn.getAliases()).stream().anyMatch(tableAliases::contains);
    }

    private static boolean aliasesContainToken(List<String> aliases, String token) {
        return normalizedAliases(aliases).stream().anyMatch(alias -> alias.equals(token) || alias.contains(token) || token.contains(alias));
    }

    private static boolean sharesDescriptionTerm(ColumnInfoPO factColumn, TableInfoPO dimensionTable) {
        Set<String> dimensionTerms = new HashSet<>(normalizedAliases(dimensionTable.getAliases()));
        dimensionTerms.add(tableToken(dimensionTable.getName()));
        String factDescription = factColumn.getDescription() == null ? "" : factColumn.getDescription().toLowerCase(Locale.ROOT);
        return normalizedAliases(factColumn.getAliases()).stream().anyMatch(dimensionTerms::contains)
            || dimensionTerms.stream().filter(term -> term.length() >= 2).anyMatch(factDescription::contains);
    }

    private static Set<String> normalizedAliases(List<String> aliases) {
        if (aliases == null) return Set.of();
        Set<String> result = new HashSet<>();
        for (String alias : aliases) {
            String normalized = normalizeText(alias);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return result;
    }

    private static String tableToken(String tableName) {
        String normalized = normalizeColumn(tableName);
        for (String prefix : List.of("t_mes_", "t_sys_", "t_wms_", "t_")) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.substring(prefix.length());
                break;
            }
        }
        return normalized.replace("_", "");
    }

    private static String businessToken(String normalizedColumn) {
        String value = normalizedColumn;
        if (value.startsWith("f_")) value = value.substring(2);
        for (String suffix : List.of("_number", "_code", "_key", "_id", "_no", "id")) {
            if (value.endsWith(suffix)) {
                value = value.substring(0, value.length() - suffix.length());
                break;
            }
        }
        return value.replace("_", "");
    }

    private static String normalizeColumn(String name) {
        if (name == null) return "";
        return name.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    }

    private static String normalizeText(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", "")
            .trim();
    }

    private static String relationKey(String factTableId, String factColumn, String dimensionTableId, String dimensionColumn,
                                      boolean caseInsensitiveColumns) {
        return factTableId + "|" + keyColumn(factColumn, caseInsensitiveColumns)
            + "|" + dimensionTableId + "|" + keyColumn(dimensionColumn, caseInsensitiveColumns);
    }

    private static String keyColumn(String column, boolean caseInsensitiveColumns) {
        return caseInsensitiveColumns ? column.toLowerCase(Locale.ROOT) : column;
    }

    private boolean usesCaseInsensitiveColumns(SubjectPO subject) {
        return dataSourceDAO.findById(subject.getDatasourceId())
            .map(DataSourcePO::getType)
            .map(type -> type == DbType.MYSQL || type == DbType.MARIADB || type == DbType.DORIS)
            .orElse(false);
    }

    private boolean canUseDictionaryValues(SubjectPO subject) {
        try {
            permissionService.requireDataSource(subject.getDatasourceId(), Permission.VIEW);
            return true;
        } catch (DatiException e) {
            if (e.getCode() == ErrorCode.PERMISSION_DENIED) return false;
            throw e;
        }
    }

    private SubjectPO requireSubject(String subjectId) {
        return subjectDAO.findById(subjectId)
            .orElseThrow(() -> new DatiException(ErrorCode.SM_SUBJECT_NOT_FOUND, subjectId));
    }
}
