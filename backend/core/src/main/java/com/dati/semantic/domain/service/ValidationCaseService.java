package com.dati.semantic.domain.service;

import com.dati.base.exception.DatiException;
import com.dati.base.RequestContext;
import com.dati.base.exception.ErrorCode;
import com.dati.permission.domain.model.Permission;
import com.dati.permission.domain.model.ResourceType;
import com.dati.auth.authentication.User;
import com.dati.datasource.repository.dao.DataSourceDAO;
import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.DataSourcePO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.mcp.domain.model.McpDataScopeType;
import com.dati.mcp.domain.model.McpResolvedTable;
import com.dati.mcp.domain.model.McpServiceDataScope;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolConfig;
import com.dati.mcp.domain.model.ToolParameter;
import com.dati.mcp.domain.service.McpTableRelationSnapshotService;
import com.dati.mcp.domain.service.QueryMetricExecutor;
import com.dati.mcp.domain.service.ToolExecutionContext;
import com.dati.mcp.server.pojo.MetricExecution;
import com.dati.mcp.server.pojo.SqlExecution;
import com.dati.mcp.server.pojo.StatementResult;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.model.ValidationCase;
import com.dati.semantic.repository.dao.MetricDAO;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.dao.SubjectTableDAO;
import com.dati.semantic.repository.dao.ValidationCaseDAO;
import com.dati.mcp.repository.mapper.McpServiceSnapshotMapper;
import com.dati.semantic.repository.mapper.MetricMapper;
import com.dati.semantic.repository.mapper.ValidationCaseMapper;
import com.dati.semantic.repository.po.MetricPO;
import com.dati.semantic.repository.po.SubjectPO;
import com.dati.semantic.repository.po.ValidationCasePO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ValidationCaseService {
    private final ValidationCaseDAO validationCaseDAO;
    private final SubjectDAO subjectDAO;
    private final MetricDAO metricDAO;
    private final SubjectTableDAO subjectTableDAO;
    private final TableInfoDAO tableInfoDAO;
    private final DataSourceDAO dataSourceDAO;
    private final QueryMetricExecutor queryMetricExecutor;
    private final McpTableRelationSnapshotService relationSnapshotService;
    private final PermissionService permissionService;

    public ValidationCaseService(ValidationCaseDAO validationCaseDAO, SubjectDAO subjectDAO,
                                 MetricDAO metricDAO, SubjectTableDAO subjectTableDAO,
                                 TableInfoDAO tableInfoDAO, DataSourceDAO dataSourceDAO,
                                 QueryMetricExecutor queryMetricExecutor,
                                 McpTableRelationSnapshotService relationSnapshotService,
                                 PermissionService permissionService) {
        this.validationCaseDAO = validationCaseDAO;
        this.subjectDAO = subjectDAO;
        this.metricDAO = metricDAO;
        this.subjectTableDAO = subjectTableDAO;
        this.tableInfoDAO = tableInfoDAO;
        this.dataSourceDAO = dataSourceDAO;
        this.queryMetricExecutor = queryMetricExecutor;
        this.relationSnapshotService = relationSnapshotService;
        this.permissionService = permissionService;
    }

    @Transactional
    public ValidationCase createCase(String subjectId, ValidationCase item) {
        SubjectPO subject = requireSubject(subjectId);
        permissionService.requireCurrentUser(ResourceType.SUBJECT, subjectId, Permission.EDIT, subject.getCreatedBy());
        validate(item);
        requireMetricInSubject(subjectId, item.getMetricId());
        item.setSubjectId(subjectId);
        if (item.getEnabled() == null) item.setEnabled(true);
        if (item.getAssertionType() == null || item.getAssertionType().isBlank()) item.setAssertionType("EXECUTION_SUCCESS");
        return ValidationCaseMapper.toModel(validationCaseDAO.save(ValidationCaseMapper.toPO(item)));
    }

    @Transactional
    public ValidationCase updateCase(String id, ValidationCase patch) {
        ValidationCasePO po = requireCase(id);
        SubjectPO subject = requireSubject(po.getSubjectId());
        permissionService.requireCurrentUser(ResourceType.SUBJECT, po.getSubjectId(), Permission.EDIT, subject.getCreatedBy());
        boolean executableInputChanged = false;
        String assertionBefore = po.getAssertionType() == null ? "EXECUTION_SUCCESS" : po.getAssertionType();
        String assertionAfter = patch.getAssertionType() != null ? patch.getAssertionType() : assertionBefore;
        if (patch.getName() != null) po.setName(patch.getName());
        if (patch.getDescription() != null) po.setDescription(patch.getDescription());
        if (patch.getArguments() != null && !Objects.equals(po.getArguments(), patch.getArguments())) executableInputChanged = true;
        if (patch.getDimensions() != null && !Objects.equals(po.getDimensions(), patch.getDimensions())) executableInputChanged = true;
        if (patch.getAssertionType() != null && !Objects.equals(po.getAssertionType(), patch.getAssertionType())) executableInputChanged = true;
        boolean expectedValueIsExecutable = "ROW_COUNT".equals(assertionBefore) || "ROW_COUNT".equals(assertionAfter);
        if (patch.getExpectedValue() != null && expectedValueIsExecutable
            && !Objects.equals(po.getExpectedValue(), patch.getExpectedValue())) executableInputChanged = true;
        if (patch.getQuestion() != null) po.setQuestion(patch.getQuestion());
        if (patch.getArguments() != null) po.setArguments(patch.getArguments());
        if (patch.getDimensions() != null) po.setDimensions(patch.getDimensions());
        if (patch.getAssertionType() != null) po.setAssertionType(patch.getAssertionType());
        if (patch.getExpectedValue() != null) po.setExpectedValue(patch.getExpectedValue());
        if (patch.getOwner() != null) po.setOwner(patch.getOwner());
        if (patch.getEnabled() != null) po.setEnabled(patch.getEnabled());
        if (patch.getUpdatedBy() != null) po.setUpdatedBy(patch.getUpdatedBy());
        if (executableInputChanged) clearLastResult(po);
        return ValidationCaseMapper.toModel(validationCaseDAO.save(po));
    }

    @Transactional
    /** Runs every enabled case of the subject and aggregates pass/fail counts. */
    public BatchRunSummary runCasesBySubject(String subjectId) {
        SubjectPO subject = requireSubject(subjectId);
        permissionService.requireCurrentUser(ResourceType.SUBJECT, subjectId, Permission.VIEW, subject.getCreatedBy());
        List<ValidationCasePO> cases = validationCaseDAO.findBySubjectId(subjectId).stream()
            .filter(po -> po.getEnabled() == null || Boolean.TRUE.equals(po.getEnabled()) || po.getEnabled())
            .toList();
        long start = System.currentTimeMillis();
        int pass = 0;
        int fail = 0;
        int error = 0;
        List<BatchRunFailure> failures = new java.util.ArrayList<>();
        for (ValidationCasePO po : cases) {
            ValidationCase result;
            try {
                result = runCase(po.getId());
            } catch (Exception e) {
                error++;
                failures.add(new BatchRunFailure(po.getId(), po.getName(), "ERROR", e.getMessage()));
                continue;
            }
            String status = result.getLastStatus() == null ? "ERROR" : result.getLastStatus();
            if ("PASS".equals(status)) {
                pass++;
            } else if ("FAIL".equals(status)) {
                fail++;
                failures.add(new BatchRunFailure(po.getId(), po.getName(), status, result.getLastMessage()));
            } else {
                error++;
                failures.add(new BatchRunFailure(po.getId(), po.getName(), status, result.getLastMessage()));
            }
        }
        return new BatchRunSummary(cases.size(), pass, fail, error,
            System.currentTimeMillis() - start, failures);
    }

    public record BatchRunSummary(int total, int pass, int fail, int error, long totalDurationMs,
                                  List<BatchRunFailure> failures) {
    }

    public record BatchRunFailure(String caseId, String caseName, String status, String message) {
    }

    public void deleteCase(String id) {
        ValidationCasePO po = requireCase(id);
        SubjectPO subject = requireSubject(po.getSubjectId());
        permissionService.requireCurrentUser(ResourceType.SUBJECT, po.getSubjectId(), Permission.EDIT, subject.getCreatedBy());
        validationCaseDAO.delete(po);
    }

    @Transactional(readOnly = true)
    public ValidationCase getCase(String id) {
        ValidationCasePO po = requireCase(id);
        SubjectPO subject = requireSubject(po.getSubjectId());
        permissionService.requireCurrentUser(ResourceType.SUBJECT, po.getSubjectId(), Permission.VIEW, subject.getCreatedBy());
        return prepareForView(ValidationCaseMapper.toModel(po), subject);
    }

    @Transactional(readOnly = true)
    public Page<ValidationCase> getCases(String subjectId, Pageable pageable) {
        SubjectPO subject = requireSubject(subjectId);
        permissionService.requireCurrentUser(ResourceType.SUBJECT, subjectId, Permission.VIEW, subject.getCreatedBy());
        return validationCaseDAO.findBySubjectId(subjectId, pageable)
            .map(ValidationCaseMapper::toModel)
            .map(item -> prepareForView(item, subject));
    }


    @Transactional
    public ValidationCase runCase(String id) {
        ValidationCasePO po = requireCase(id);
        SubjectPO subject = requireSubject(po.getSubjectId());
        permissionService.requireCurrentUser(ResourceType.SUBJECT, po.getSubjectId(), Permission.EDIT, subject.getCreatedBy());
        if (!Boolean.TRUE.equals(po.getEnabled())) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "validation case is disabled");
        }
        MetricPO metric = metricDAO.findById(po.getMetricId())
            .orElseThrow(() -> new DatiException(ErrorCode.SM_METRIC_NOT_FOUND, po.getMetricId()));
        if (!po.getSubjectId().equals(metric.getSubjectId())) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "metric does not belong to validation case subject");
        }
        boolean dataSourceMissing = !dataSourceDAO.existsById(metric.getDataSourceId());
        if (!dataSourceMissing) {
            permissionService.requireDataSource(metric.getDataSourceId(), Permission.VIEW);
        }

        long started = System.currentTimeMillis();
        po.setLastRunAt(Instant.now());
        po.setLastMetricRevision(metric.getRevision());
        try {
            if (dataSourceMissing) {
                throw new DatiException(ErrorCode.DS_NOT_FOUND, metric.getDataSourceId());
            }
            validateDimensions(po, metric);
            SqlExecution execution = executeMetric(po, metric);
            int returnedRows = firstSelectRows(execution);
            boolean sqlSuccess = execution.results() != null && execution.results().stream().allMatch(StatementResult::success);
            if (!sqlSuccess) {
                po.setLastStatus("ERROR");
                po.setLastMessage(firstError(execution));
            } else {
                applyAssertion(po, execution, returnedRows);
            }
            Map<String, Object> resultEvidence = new HashMap<>(evidence(execution, returnedRows));
            resultEvidence.put("relation_fingerprint", relationFingerprint(po.getSubjectId()));
            po.setLastEvidence(resultEvidence);
        } catch (Exception e) {
            po.setLastStatus("ERROR");
            po.setLastMessage(e.getMessage());
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("error", e.getMessage());
            po.setLastEvidence(evidence);
        }
        po.setLastDurationMs(System.currentTimeMillis() - started);
        return ValidationCaseMapper.toModel(validationCaseDAO.save(po));
    }

    private void clearLastResult(ValidationCasePO po) {
        po.setLastStatus(null);
        po.setLastMessage(null);
        po.setLastRunAt(null);
        po.setLastDurationMs(null);
        po.setLastMetricRevision(null);
        po.setLastEvidence(new HashMap<>());
    }

    private boolean isTruncated(SqlExecution execution) {
        return execution.results() != null && execution.results().stream()
            .anyMatch(result -> result instanceof StatementResult.SelectResult select && select.truncated());
    }

    private ValidationCase prepareForView(ValidationCase item, SubjectPO subject) {
        DataSourcePO dataSource = dataSourceDAO.findById(subject.getDatasourceId()).orElse(null);
        if (dataSource == null) {
            item.setLastStatus("ERROR");
            item.setLastMessage("data source is unavailable");
            item.setLastEvidence(Map.of("error", "data source is unavailable"));
            return item;
        }
        if (item.getLastEvidence() == null || !item.getLastEvidence().containsKey("sample_rows")
            || canViewDataSource(dataSource)) {
            return item;
        }
        Map<String, Object> redacted = new HashMap<>(item.getLastEvidence());
        redacted.remove("sample_rows");
        redacted.put("sample_rows_redacted", true);
        item.setLastEvidence(redacted);
        return item;
    }

    private boolean canViewDataSource(DataSourcePO dataSource) {
        User user = RequestContext.getUser();
        return permissionService.can(user.getId(), user.getName(), ResourceType.DATA_SOURCE,
            dataSource.getId(), Permission.VIEW, dataSource.getCreatedBy());
    }

    private void validateDimensions(ValidationCasePO po, MetricPO metric) {
        if (po.getDimensions() == null || po.getDimensions().isEmpty()) {
            return;
        }
        List<String> invalid = po.getDimensions().stream()
            .filter(dimension -> metric.getAllowedDimensions() == null || !metric.getAllowedDimensions().contains(dimension))
            .toList();
        if (!invalid.isEmpty()) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER,
                "dimensions are not allowed for metric: " + String.join(", ", invalid));
        }
    }

    private SqlExecution executeMetric(ValidationCasePO po, MetricPO metric) {
        Map<String, Object> arguments = new HashMap<>(po.getArguments() != null ? po.getArguments() : Map.of());
        List<String> dimensions = po.getDimensions() != null ? po.getDimensions() : List.of();
        McpServiceDataScope scope = subjectScope(po.getSubjectId());
        com.dati.mcp.domain.model.McpServiceSnapshot.MetricDraft draft = McpServiceSnapshotMapper.toMetricDraft(
            MetricMapper.toMetric(metric));
        List<com.dati.mcp.domain.model.McpServiceSnapshot.TableRelationDraft> relations =
            relationSnapshotService.buildRelationDrafts(List.of(po.getSubjectId()), List.of(scope));
        ToolExecutionContext ctx = new ToolExecutionContext("validation-case", McpToolType.QUERY_METRIC,
            new ToolConfig.QueryMetricConfig(),
            new com.dati.mcp.domain.model.param.QueryMetricArgs(metric.getId(), arguments, dimensions),
            List.of(scope), List.of(draft), relations);
        MetricExecution execution = (MetricExecution) queryMetricExecutor.execute(ctx);
        return execution.execution();
    }

    private String relationFingerprint(String subjectId) {
        McpServiceDataScope scope = subjectScope(subjectId);
        return relationSnapshotService.buildRelationDrafts(List.of(subjectId), List.of(scope)).stream()
            .map(relation -> relation.factDataSourceId() + "." + relation.factSchema() + "." + relation.factTableName() + "." + relation.factColumn()
                + "->" + relation.dimensionDataSourceId() + "." + relation.dimensionSchema() + "." + relation.dimensionTableName() + "." + relation.dimensionColumn()
                + "|" + relation.cardinality() + "|" + relation.revision())
            .sorted()
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    private McpServiceDataScope subjectScope(String subjectId) {
        List<String> tableIds = subjectTableDAO.findBySubjectId(subjectId).stream()
            .map(com.dati.semantic.repository.po.SubjectTablePO::getTableId).toList();
        List<TableInfoPO> tables = tableInfoDAO.findAllById(tableIds);
        McpServiceDataScope scope = new McpServiceDataScope();
        scope.setScopeType(McpDataScopeType.SUBJECT);
        scope.setReferenceId(subjectId);
        scope.setResolvedTables(tables.stream()
            .map(table -> new McpResolvedTable(table.getDataSourceId(), table.getSchema(), table.getName()))
            .toList());
        return scope;
    }

    private void applyAssertion(ValidationCasePO po, SqlExecution execution, int returnedRows) {
        String assertionType = po.getAssertionType() == null ? "EXECUTION_SUCCESS" : po.getAssertionType();
        switch (assertionType) {
            case "EXECUTION_SUCCESS" -> {
                po.setLastStatus("PASS");
                po.setLastMessage("execution succeeded");
            }
            case "NON_EMPTY" -> {
                if (returnedRows > 0) {
                    po.setLastStatus("PASS");
                    po.setLastMessage("execution returned rows");
                } else {
                    po.setLastStatus("FAIL");
                    po.setLastMessage("expected non-empty result but query returned zero rows");
                }
            }
            case "ROW_COUNT" -> {
                int expected = Integer.parseInt(po.getExpectedValue());
                if (isTruncated(execution)) {
                    po.setLastStatus("FAIL");
                    po.setLastMessage("row count assertion cannot be trusted because the result was truncated");
                } else if (returnedRows == expected) {
                    po.setLastStatus("PASS");
                    po.setLastMessage("row count matched");
                } else {
                    po.setLastStatus("FAIL");
                    po.setLastMessage("expected row count " + expected + " but got " + returnedRows);
                }
            }
            default -> throw new DatiException(ErrorCode.INVALID_PARAMETER, "unsupported assertion type: " + assertionType);
        }
    }

    private int firstSelectRows(SqlExecution execution) {
        if (execution.results() == null) return 0;
        return execution.results().stream()
            .filter(result -> "SELECT".equals(result.type()) && result.success())
            .map(result -> (StatementResult.SelectResult) result)
            .map(StatementResult.SelectResult::returnedRows)
            .findFirst().orElse(0);
    }

    private String firstError(SqlExecution execution) {
        if (execution.results() == null) return "SQL execution failed";
        return execution.results().stream().map(StatementResult::errorMessage)
            .filter(message -> message != null && !message.isBlank()).findFirst().orElse("SQL execution failed");
    }

    private Map<String, Object> evidence(SqlExecution execution, int returnedRows) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("returned_rows", returnedRows);
        evidence.put("truncated", execution.results() != null && execution.results().stream()
            .anyMatch(result -> result instanceof StatementResult.SelectResult select && select.truncated()));
        evidence.put("sql_fingerprint", Integer.toHexString(execution.executedSql() == null ? 0 : execution.executedSql().hashCode()));
        execution.results().stream()
            .filter(result -> result instanceof StatementResult.SelectResult)
            .map(result -> (StatementResult.SelectResult) result)
            .findFirst()
            .ifPresent(result -> {
                evidence.put("columns", result.columns());
                evidence.put("sample_rows", result.rows() == null ? List.of() : result.rows().stream().limit(5).toList());
            });
        return evidence;
    }

    @Transactional(readOnly = true)
    public List<String> publishBlockingMessages(List<String> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return List.of();
        }
        return validationCaseDAO.findBySubjectIdIn(subjectIds).stream()
            .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
            .map(this::publishBlockingMessage)
            .filter(message -> message != null)
            .toList();
    }

    private String publishBlockingMessage(ValidationCasePO item) {
        MetricPO metric = metricDAO.findById(item.getMetricId()).orElse(null);
        if (metric == null) {
            return item.getName() + ": metric is missing";
        }
        if (!"PASS".equals(item.getLastStatus())) {
            return item.getName() + ": latest validation did not pass";
        }
        if (!Objects.equals(item.getLastMetricRevision(), metric.getRevision())) {
            return item.getName() + ": metric changed after validation";
        }
        Object validatedRelations = item.getLastEvidence() == null ? null : item.getLastEvidence().get("relation_fingerprint");
        if (!Objects.equals(validatedRelations, relationFingerprint(item.getSubjectId()))) {
            return item.getName() + ": table relations changed after validation";
        }
        return null;
    }

    private void validate(ValidationCase item) {
        if (item.getMetricId() == null || item.getMetricId().isBlank()
            || item.getQuestion() == null || item.getQuestion().isBlank()
            || item.getName() == null || item.getName().isBlank()) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "name, metricId and question are required");
        }
    }

    private void requireMetricInSubject(String subjectId, String metricId) {
        MetricPO metric = metricDAO.findById(metricId)
            .orElseThrow(() -> new DatiException(ErrorCode.SM_METRIC_NOT_FOUND, metricId));
        if (!subjectId.equals(metric.getSubjectId())) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "metric does not belong to subject");
        }
    }

    private SubjectPO requireSubject(String subjectId) {
        return subjectDAO.findById(subjectId)
            .orElseThrow(() -> new DatiException(ErrorCode.SM_SUBJECT_NOT_FOUND, subjectId));
    }

    private ValidationCasePO requireCase(String id) {
        return validationCaseDAO.findById(id)
            .orElseThrow(() -> new DatiException(ErrorCode.INVALID_PARAMETER, "validation case not found: " + id));
    }
}
