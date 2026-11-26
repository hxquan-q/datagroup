package com.dati.semantic.domain.service;

import com.dati.base.exception.DatiException;
import com.dati.base.exception.ErrorCode;
import com.dati.permission.domain.model.Permission;
import com.dati.permission.domain.model.ResourceType;
import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.mcp.domain.model.McpDataScopeType;
import com.dati.mcp.domain.model.McpResolvedTable;
import com.dati.mcp.domain.model.McpServiceDataScope;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolConfig;
import com.dati.mcp.domain.model.ToolParameter;
import com.dati.mcp.domain.service.ParameterizedSqlExecutor;
import com.dati.mcp.domain.service.ToolExecutionContext;
import com.dati.mcp.server.pojo.SqlExecution;
import com.dati.mcp.server.pojo.StatementResult;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.model.ValidationCase;
import com.dati.semantic.repository.dao.MetricDAO;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.dao.SubjectTableDAO;
import com.dati.semantic.repository.dao.ValidationCaseDAO;
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

@Service
public class ValidationCaseService {
    private final ValidationCaseDAO validationCaseDAO;
    private final SubjectDAO subjectDAO;
    private final MetricDAO metricDAO;
    private final SubjectTableDAO subjectTableDAO;
    private final TableInfoDAO tableInfoDAO;
    private final ParameterizedSqlExecutor parameterizedSqlExecutor;
    private final PermissionService permissionService;

    public ValidationCaseService(ValidationCaseDAO validationCaseDAO, SubjectDAO subjectDAO,
                                 MetricDAO metricDAO, SubjectTableDAO subjectTableDAO,
                                 TableInfoDAO tableInfoDAO, ParameterizedSqlExecutor parameterizedSqlExecutor,
                                 PermissionService permissionService) {
        this.validationCaseDAO = validationCaseDAO;
        this.subjectDAO = subjectDAO;
        this.metricDAO = metricDAO;
        this.subjectTableDAO = subjectTableDAO;
        this.tableInfoDAO = tableInfoDAO;
        this.parameterizedSqlExecutor = parameterizedSqlExecutor;
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
        if (patch.getName() != null) po.setName(patch.getName());
        if (patch.getDescription() != null) po.setDescription(patch.getDescription());
        if (patch.getQuestion() != null) po.setQuestion(patch.getQuestion());
        if (patch.getArguments() != null) po.setArguments(patch.getArguments());
        if (patch.getDimensions() != null) po.setDimensions(patch.getDimensions());
        if (patch.getAssertionType() != null) po.setAssertionType(patch.getAssertionType());
        if (patch.getExpectedValue() != null) po.setExpectedValue(patch.getExpectedValue());
        if (patch.getOwner() != null) po.setOwner(patch.getOwner());
        if (patch.getEnabled() != null) po.setEnabled(patch.getEnabled());
        if (patch.getUpdatedBy() != null) po.setUpdatedBy(patch.getUpdatedBy());
        return ValidationCaseMapper.toModel(validationCaseDAO.save(po));
    }

    @Transactional
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
        return ValidationCaseMapper.toModel(po);
    }

    @Transactional(readOnly = true)
    public Page<ValidationCase> getCases(String subjectId, Pageable pageable) {
        SubjectPO subject = requireSubject(subjectId);
        permissionService.requireCurrentUser(ResourceType.SUBJECT, subjectId, Permission.VIEW, subject.getCreatedBy());
        return validationCaseDAO.findBySubjectId(subjectId, pageable).map(ValidationCaseMapper::toModel);
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

        long started = System.currentTimeMillis();
        po.setLastRunAt(Instant.now());
        po.setLastMetricRevision(metric.getRevision());
        try {
            SqlExecution execution = executeMetric(po, metric);
            int returnedRows = firstSelectRows(execution);
            boolean sqlSuccess = execution.results() != null && execution.results().stream().allMatch(StatementResult::success);
            if (!sqlSuccess) {
                po.setLastStatus("ERROR");
                po.setLastMessage(firstError(execution));
            } else {
                applyAssertion(po, execution, returnedRows);
            }
            po.setLastEvidence(evidence(execution, returnedRows));
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

    private SqlExecution executeMetric(ValidationCasePO po, MetricPO metric) {
        ToolConfig.ParamSqlConfig config = new ToolConfig.ParamSqlConfig();
        config.setDataSourceId(metric.getDataSourceId());
        config.setSqlTemplate(metric.getSqlTemplate());
        config.setParameters(metric.getParameters().stream()
            .map(parameter -> new ToolParameter(parameter.getName(), parameter.getType(), parameter.isRequired(),
                parameter.getDefaultValue(), parameter.getDescription()))
            .toList());
        config.setTimeout(metric.getTimeout());
        config.setMaxRows(metric.getMaxRows());
        Map<String, Object> arguments = new HashMap<>(po.getArguments() != null ? po.getArguments() : Map.of());
        if (po.getDimensions() != null && !po.getDimensions().isEmpty()) {
            arguments.put("dimensions", po.getDimensions());
        }
        ToolExecutionContext ctx = new ToolExecutionContext("validation-case", McpToolType.PARAMETERIZED_SQL,
            config, arguments, List.of(subjectScope(po.getSubjectId())));
        return (SqlExecution) parameterizedSqlExecutor.execute(ctx);
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
                if (returnedRows == expected) {
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
