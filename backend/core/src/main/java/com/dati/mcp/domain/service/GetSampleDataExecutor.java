package com.dati.mcp.domain.service;

import com.dati.datasource.domain.model.DataSource;
import com.dati.datasource.domain.service.DataSourceService;
import com.dati.db.HikariPoolManager;
import com.dati.db.JdbcConnector;
import com.dati.db.analysis.TableRef;
import com.dati.mcp.domain.model.McpToolType;
import com.dati.mcp.domain.model.ToolError;
import com.dati.mcp.domain.model.param.GetSampleDataArgs;
import com.dati.mcp.server.pojo.SampleData;
import com.dati.mcp.server.pojo.ToolTestData;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class GetSampleDataExecutor implements ToolExecutor {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final ScopeValidator scopeValidator;
    private final DataSourceService dataSourceService;

    public GetSampleDataExecutor(ScopeValidator scopeValidator, DataSourceService dataSourceService) {
        this.scopeValidator = scopeValidator;
        this.dataSourceService = dataSourceService;
    }

    @Override
    public McpToolType getToolType() {
        return McpToolType.GET_SAMPLE_DATA;
    }

    @Override
    public ToolTestData execute(ToolExecutionContext ctx) {
        GetSampleDataArgs args = ctx.args(GetSampleDataArgs.class);
        String schema = null;
        String table = args.tableName().trim();
        int dot = table.indexOf('.');
        if (dot > 0) {
            schema = table.substring(0, dot);
            table = table.substring(dot + 1);
        }
        validateIdentifier(table, "table_name");
        if (schema != null) {
            validateIdentifier(schema, "schema");
        }
        List<String> columns = new ArrayList<>(args.columns());
        for (String column : columns) {
            validateIdentifier(column.trim(), "column");
        }

        DataSource dataSource = dataSourceService.getDataSourceInternal(args.dataSourceId())
            .orElseThrow(() -> new ToolExecuteException(ToolError.DATA_SOURCE_NOT_FOUND, args.dataSourceId()));

        scopeValidator.validate(ctx.scopeItems(), args.dataSourceId(),
            Set.of(new TableRef(schema, table)), Set.of(), dataSource.getDefaultSchema());

        String columnSql = columns.isEmpty() ? "*"
            : String.join(", ", columns.stream().map(String::trim).toList());
        String tableSql = schema != null ? schema + "." + table : table;
        String sql = "SELECT " + columnSql + " FROM " + tableSql + " LIMIT " + args.limit();

        List<String> resultColumns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        List<String> maskedColumns = new ArrayList<>();
        JdbcConnector connector = new JdbcConnector(dataSource);
        try (Connection conn = HikariPoolManager.getConnection(connector);
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(15);
            stmt.setMaxRows(args.limit());
            try (ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String name = meta.getColumnLabel(i);
                resultColumns.add(name);
                if (DataMasker.isSensitive(name)) {
                    maskedColumns.add(name);
                }
            }
            while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(DataMasker.mask(resultColumns.get(i - 1), rs.getObject(i)));
                }
                rows.add(row);
            }
            }
        } catch (java.sql.SQLException e) {
            throw new ToolExecuteException(ToolError.SQL_EXECUTION_ERROR, e.getMessage());
        }
        return new SampleData(tableSql, List.copyOf(resultColumns), rows, List.copyOf(maskedColumns));
    }

    private void validateIdentifier(String identifier, String label) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new ToolExecuteException(ToolError.PARAM_INVALID,
                label + " must be a plain identifier (letters, digits, underscore): " + identifier);
        }
    }
}
