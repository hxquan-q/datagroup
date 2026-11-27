package com.dati.mcp.domain.service;

import com.dati.datasource.domain.model.DataSource;
import com.dati.datasource.domain.service.DataSourceService;
import com.dati.db.HikariPoolManager;
import com.dati.db.analysis.SqlOperationType;
import com.dati.db.JdbcConnector;
import com.dati.db.analysis.SqlAnalysisResult;
import com.dati.db.analysis.SqlAnalyzer;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.SqlPolicy;
import com.dati.mcp.domain.model.ToolConfig.ExecuteSqlConfig;
import com.dati.mcp.domain.model.ToolError;
import com.dati.mcp.domain.model.param.ExecuteSqlArgs;
import com.dati.mcp.server.pojo.SqlExecution;
import com.dati.mcp.server.pojo.StatementResult;
import com.dati.mcp.server.pojo.ToolTestData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Slf4j
@Component
public class ExecuteSqlExecutor implements ToolExecutor {

    private final ScopeValidator scopeValidator;
    private final DataSourceService dataSourceService;
    private final TableAutoRegistrationService tableAutoRegistrationService;
    private final com.dati.datasource.repository.dao.TableInfoDAO tableInfoDAO;

    public ExecuteSqlExecutor(ScopeValidator scopeValidator,
                              DataSourceService dataSourceService,
                              TableAutoRegistrationService tableAutoRegistrationService,
                              com.dati.datasource.repository.dao.TableInfoDAO tableInfoDAO) {
        this.scopeValidator = scopeValidator;
        this.dataSourceService = dataSourceService;
        this.tableAutoRegistrationService = tableAutoRegistrationService;
        this.tableInfoDAO = tableInfoDAO;
    }

    @Override
    public McpToolType getToolType() {
        return McpToolType.EXECUTE_SQL;
    }

    @Override
    public ToolTestData execute(ToolExecutionContext ctx) {
        ExecuteSqlArgs args = ctx.args(ExecuteSqlArgs.class);
        String dsId = args.dataSourceId();
        String sql = args.sql();

        ExecuteSqlConfig config = (ExecuteSqlConfig) ctx.config();

        SqlPolicy policy = config.getSqlPolicy();

        SqlAnalysisResult analysis = SqlAnalyzer.analyze(sql);

        policy.validate(analysis);

        DataSource dataSource = dataSourceService.getDataSourceInternal(dsId)
            .orElseThrow(() -> new ToolExecuteException(ToolError.DATA_SOURCE_NOT_FOUND, dsId));

        scopeValidator.validate(ctx.scopeItems(), dsId,
            analysis.referencedTables(), analysis.createdTables(),
            dataSource.getDefaultSchema());

        enforceBlockedTables(analysis, dsId);

        JdbcConnector connector = new JdbcConnector(dataSource);
        List<StatementResult> results;
        try (Connection conn = HikariPoolManager.getConnection(connector)) {
            List<Object> params = args.params() != null ? args.params() : List.of();
            String validationSql = params.isEmpty() ? sql : inlineBindValues(sql, params);
            enforceTimeWindows(validationSql, analysis, dsId);
            enforcePlanGuardrail(conn, sql, params, analysis, config, dataSource.getType());
            sql = applyServerSideTimeout(sql, analysis, dataSource.getType(), config.getTimeout());
            if (!params.isEmpty()) {
                if (analysis.type() == SqlOperationType.MULTI) {
                    throw new ToolExecuteException(ToolError.SQL_EXECUTION_ERROR,
                        "Bind parameters are only supported for single-statement SQL");
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setQueryTimeout(config.getTimeout());
                    ps.setMaxRows(SqlExecutorHelper.probeRowLimit(config.getMaxRows()));
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    ps.execute();
                    results = SqlExecutorHelper.collect(ps, config.getMaxRows());
                }
            } else {
                try (Statement stmt = conn.createStatement()) {
                    stmt.setQueryTimeout(config.getTimeout());
                    stmt.setMaxRows(SqlExecutorHelper.probeRowLimit(config.getMaxRows()));
                    stmt.execute(sql);
                    results = SqlExecutorHelper.collect(stmt, config.getMaxRows());
                }
            }
        } catch (SQLException e) {
            throw new ToolExecuteException(ToolError.SQL_EXECUTION_ERROR, e.getMessage());
        }

        registerCreatedTables(dsId, analysis, dataSource.getDefaultSchema(), ctx.serviceId());

        return new SqlExecution(sql, results, null);
    }

    /**
     * Replaces {@code ?} placeholders outside string literals and comments with their escaped
     * bind values, producing SQL used only for validation (plan and time-window checks);
     * execution always uses the PreparedStatement with real binding.
     */
    static String inlineBindValues(String sql, List<Object> params) {
        StringBuilder out = new StringBuilder(sql.length() + 16 * params.size());
        int paramIndex = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inLineComment) {
                out.append(c);
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inSingle) {
                out.append(c);
                if (c == '\'' ) {
                    inSingle = false;
                }
                continue;
            }
            if (inDouble) {
                out.append(c);
                if (c == '"') {
                    inDouble = false;
                }
                continue;
            }
            if (inBacktick) {
                out.append(c);
                if (c == '`') {
                    inBacktick = false;
                }
                continue;
            }
            if (inBlockComment) {
                out.append(c);
                if (c == '*' && i + 1 < sql.length() && sql.charAt(i + 1) == '/') {
                    out.append('/');
                    i++;
                    inBlockComment = false;
                }
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                inBlockComment = true;
                out.append(c);
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                inLineComment = true;
                out.append(c);
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                out.append(c);
                continue;
            }
            if (c == '"') {
                inDouble = true;
                out.append(c);
                continue;
            }
            if (c == '`') {
                inBacktick = true;
                out.append(c);
                continue;
            }
            if (c == '?' && paramIndex < params.size()) {
                out.append(literalOf(params.get(paramIndex++)));
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String literalOf(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    /** Rejects free SQL on tables closed to it; they are reachable only through parameterized tools. */
    private void enforceBlockedTables(SqlAnalysisResult analysis, String dsId) {
        if (analysis.referencedTables().isEmpty()) {
            return;
        }
        java.util.List<com.dati.datasource.repository.po.TableInfoPO> registered =
            tableInfoDAO.findByDataSourceId(dsId);
        java.util.List<com.dati.datasource.repository.po.TableInfoPO> blockedTables = new java.util.ArrayList<>();
        for (com.dati.datasource.repository.po.TableInfoPO po : registered) {
            if (po.isSqlBlocked()) {
                blockedTables.add(po);
            }
        }
        if (blockedTables.isEmpty()) {
            return;
        }
        java.util.List<String> hits = new java.util.ArrayList<>();
        for (com.dati.db.analysis.TableRef ref : analysis.referencedTables()) {
            if (ref.name() == null) {
                continue;
            }
            for (com.dati.datasource.repository.po.TableInfoPO po : blockedTables) {
                if (!po.getName().equalsIgnoreCase(ref.name())) {
                    continue;
                }
                if (ref.schema() != null && po.getSchema() != null
                        && !po.getSchema().equalsIgnoreCase(ref.schema())) {
                    continue;
                }
                hits.add(ref.name());
                break;
            }
        }
        if (!hits.isEmpty()) {
            throw new ToolExecuteException(ToolError.SQL_EXECUTION_ERROR,
                "Free SQL is not allowed on large tables: " + String.join(", ", hits)
                    + ". Use the service's parameterized tools or query a narrower aggregation instead.");
        }
    }

    /**
     * Rejects queries on tables marked as requiring a time window when the SQL
     * lacks a bounded filter on the table's configured time column.
     */
    private void enforceTimeWindows(String sql, SqlAnalysisResult analysis, String dsId) {
        if (analysis.referencedTables().isEmpty()) {
            return;
        }
        java.util.List<com.dati.datasource.repository.po.TableInfoPO> registered =
            tableInfoDAO.findByDataSourceId(dsId);
        java.util.Map<String, TimeWindowEnforcer.Rule> rules = new java.util.HashMap<>();
        for (com.dati.db.analysis.TableRef ref : analysis.referencedTables()) {
            if (ref.name() == null) {
                continue;
            }
            for (com.dati.datasource.repository.po.TableInfoPO po : registered) {
                if (!po.isTimeWindowRequired() || po.getTimeColumn() == null || po.getTimeColumn().isBlank()) {
                    continue;
                }
                if (!po.getName().equalsIgnoreCase(ref.name())) {
                    continue;
                }
                if (ref.schema() != null && po.getSchema() != null
                        && !po.getSchema().equalsIgnoreCase(ref.schema())) {
                    continue;
                }
                rules.putIfAbsent(po.getName().toLowerCase(java.util.Locale.ROOT),
                    new TimeWindowEnforcer.Rule(po.getName(), po.getTimeColumn(), po.getMaxRangeDays()));
            }
        }
        if (rules.isEmpty()) {
            return;
        }
        java.util.List<TimeWindowEnforcer.Violation> violations = TimeWindowEnforcer.check(sql, rules);
        if (!violations.isEmpty()) {
            throw new ToolExecuteException(ToolError.SQL_EXECUTION_ERROR,
                TimeWindowEnforcer.rejectionMessage(violations));
        }
    }

    private void enforcePlanGuardrail(Connection conn, String sql, java.util.List<Object> params,
                                      SqlAnalysisResult analysis,
                                      ExecuteSqlConfig config, com.dati.db.DbType dbType) {
        if (config.getMaxExplainRows() <= 0 || !ExplainGuardrail.supports(dbType)) {
            return;
        }
        try {
            if (analysis.type() == SqlOperationType.SELECT) {
                runExplain(conn, sql, params, config);
            } else if (analysis.type() == SqlOperationType.MULTI) {
                explainSelectStatements(conn, sql, config);
            }
        } catch (ToolExecuteException e) {
            throw e;
        } catch (Exception e) {
            log.debug("EXPLAIN guardrail skipped: {}", e.getMessage());
        }
    }

    private void explainSelectStatements(Connection conn, String sql, ExecuteSqlConfig config) throws SQLException {
        net.sf.jsqlparser.statement.Statements statements;
        try {
            statements = net.sf.jsqlparser.parser.CCJSqlParserUtil.parseStatements(sql);
        } catch (net.sf.jsqlparser.JSQLParserException e) {
            log.debug("EXPLAIN guardrail skipped unparseable multi-statement: {}", e.getMessage());
            return;
        }
        for (net.sf.jsqlparser.statement.Statement statement : statements.getStatements()) {
            if (statement instanceof net.sf.jsqlparser.statement.select.Select) {
                runExplain(conn, statement.toString(), java.util.List.of(), config);
            }
        }
    }

    private void runExplain(Connection conn, String sql, java.util.List<Object> params, ExecuteSqlConfig config) throws SQLException {
        try (PreparedStatement explain = conn.prepareStatement("EXPLAIN FORMAT=JSON " + sql)) {
            explain.setQueryTimeout(Math.max(1, config.getTimeout()));
            for (int i = 0; i < params.size(); i++) {
                explain.setObject(i + 1, params.get(i));
            }
            try (java.sql.ResultSet rs = explain.executeQuery()) {
            if (rs == null || !rs.next()) {
                return;
            }
            String planJson = rs.getString(1);
            if (planJson == null) {
                return;
            }
            List<ExplainGuardrail.Offender> offenders =
                ExplainGuardrail.inspectMySqlPlan(planJson, config.getMaxExplainRows());
            if (!offenders.isEmpty()) {
                throw new ToolExecuteException(ToolError.SCAN_LIMIT_EXCEEDED,
                    ExplainGuardrail.rejectionMessage(offenders, config.getMaxExplainRows()));
            }
            }
        }
    }

    /** Server-side execution cap for MySQL: the optimizer hint aborts the query
     * inside the engine even when the client-side timeout cannot reach it. */
    static String applyServerSideTimeout(String sql, SqlAnalysisResult analysis, com.dati.db.DbType type, int timeoutSeconds) {
        if (type != com.dati.db.DbType.MYSQL || analysis.type() != SqlOperationType.SELECT || timeoutSeconds <= 0) {
            return sql;
        }
        int ms = timeoutSeconds * 1000;
        return sql.replaceFirst("(?is)^(\s*(?:/\\*.*?\\*/\s*)*select)\s", "$1 /*+ MAX_EXECUTION_TIME(" + ms + ") */ ");
    }

    private void registerCreatedTables(String dsId, SqlAnalysisResult analysis,
                                       String defaultSchema, String serviceId) {
        if (analysis.createdTables().isEmpty()) {
            return;
        }
        try {
            tableAutoRegistrationService.registerCreatedTables(
                dsId, analysis.createdTables(), defaultSchema, serviceId);
        } catch (Exception e) {
            log.warn("Failed to auto-register tables created by SQL on data source {}: {}",
                dsId, e.getMessage());
        }
    }
}
