package com.dati.mcp.domain.service;

import com.dati.db.analysis.TableRef;
import com.dati.mcp.domain.model.McpDataScopeType;
import com.dati.mcp.domain.model.McpServiceDataScope;
import com.dati.mcp.domain.model.ToolError;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.po.SubjectPO;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class ScopeValidator {

    private final SubjectDAO subjectDAO;

    public ScopeValidator(SubjectDAO subjectDAO) {
        this.subjectDAO = subjectDAO;
    }

    /**
     * Validates that the data source is covered by the service scope and that referenced or
     * created tables stay within it. Callers load the data source first and pass its default
     * schema.
     * Operation-type permissions (including DDL allowance) are enforced by SqlPolicy before this call.
     */
    public void validate(List<McpServiceDataScope> scopeItems, String dsId,
                         Set<TableRef> referencedTables, Set<TableRef> createdTables,
                         @Nullable String defaultSchema) {
        validateScopeCoverage(scopeItems, dsId);
        validateCreatedTables(scopeItems, dsId, createdTables, defaultSchema);
        validateReferencedTables(scopeItems, dsId, referencedTables, defaultSchema);
    }

    private void validateScopeCoverage(List<McpServiceDataScope> scopeItems, String dsId) {
        if (scopeItems == null || scopeItems.isEmpty()) {
            throw new ToolExecuteException(ToolError.SCOPE_VIOLATION, "No data scope configured");
        }

        if (!coveredDataSourceIds(scopeItems).contains(dsId)) {
            throw new ToolExecuteException(ToolError.SCOPE_VIOLATION,
                "Data source " + dsId + " not in service scope");
        }
    }

    private void validateReferencedTables(List<McpServiceDataScope> scopeItems, String dsId,
                                          Set<TableRef> tableRefs, @Nullable String defaultSchema) {
        if (tableRefs == null || tableRefs.isEmpty()) return;

        Set<String> allowedSchemas = McpDataScopeSchemas.allowedSchemas(scopeItems, dsId);
        Set<TableRef> allowed = null;
        for (TableRef ref : tableRefs) {
            String schema = McpDataScopeSchemas.effectiveSchema(ref, defaultSchema);
            if (schema != null && allowedSchemas.contains(schema)) continue;

            if (allowed == null) {
                allowed = buildAllowedTableRefs(scopeItems, dsId);
            }
            if (allowed.contains(ref)) continue;
            if (tryResolve(ref, defaultSchema, allowed)) continue;
            boolean hasSubjectScope = scopeItems.stream()
                .anyMatch(item -> item.getScopeType() == com.dati.mcp.domain.model.McpDataScopeType.SUBJECT);
            boolean hasSchemaWide = scopeItems.stream()
                .anyMatch(item -> item.getAccessMode() == com.dati.mcp.domain.model.McpDataScopeAccessMode.SCHEMA_SCOPE);
            String ownerAction;
            if (hasSubjectScope) {
                ownerAction = "ask the service owner to import this table into the subject";
            } else if (hasSchemaWide) {
                ownerAction = "ask the service owner to widen the service's schema scope";
            } else {
                ownerAction = "ask the service owner to import this table into the service's data scope";
            }
            throw new ToolExecuteException(ToolError.SCOPE_VIOLATION,
                "Table " + ref.qualifiedName() + " is outside this service's data scope"
                    + " (system catalogs are never accessible)."
                    + " If the service exposes them, use list_tables_and_terms or search_tables_and_terms"
                    + " to discover available tables, or " + ownerAction + ".");
        }
    }

    private void validateCreatedTables(List<McpServiceDataScope> scopeItems, String dsId,
                                       Set<TableRef> createdTables, @Nullable String defaultSchema) {
        if (createdTables == null || createdTables.isEmpty()) return;

        Set<String> allowedSchemas = McpDataScopeSchemas.allowedSchemas(scopeItems, dsId);
        if (allowedSchemas.isEmpty()) {
            throw new ToolExecuteException(ToolError.SCOPE_VIOLATION,
                "No schema scope configured for data source " + dsId);
        }

        for (TableRef ref : createdTables) {
            String schema = McpDataScopeSchemas.effectiveSchema(ref, defaultSchema);
            if (schema == null || schema.isBlank()) {
                throw new ToolExecuteException(ToolError.SCOPE_VIOLATION,
                    "Cannot resolve schema for created table " + ref.qualifiedName());
            }
            if (!allowedSchemas.contains(schema)) {
                throw new ToolExecuteException(ToolError.SCOPE_VIOLATION,
                    "Schema " + schema + " is not in the service schema scope");
            }
        }
    }

    private Set<String> coveredDataSourceIds(List<McpServiceDataScope> scopeItems) {
        return McpScopeResolution.resolvedDataSourceIds(scopeItems);
    }

    /**
     * Metadata-write scope check: the data source must be covered by the service scope
     * (direct DATA_SOURCE ref or via SUBJECT scope tables). Table-level scope is not
     * required — metadata writes are not data access.
     */
    public void validateDataSource(List<McpServiceDataScope> scopeItems, String dsId) {
        validateScopeCoverage(scopeItems, dsId);
    }

    /**
     * Resolves the subjectId whose name matches subjectName, scanning scope SUBJECT
     * items in order (first match wins). Throws SCOPE_VIOLATION when no subject in
     * scope has that name.
     */
    public String resolveSubjectInScope(List<McpServiceDataScope> scopeItems, String subjectName) {
        if (scopeItems == null || scopeItems.isEmpty()) {
            throw new ToolExecuteException(ToolError.SCOPE_VIOLATION, "No data scope configured");
        }
        for (McpServiceDataScope scope : scopeItems) {
            if (scope.getScopeType() != McpDataScopeType.SUBJECT) {
                continue;
            }
            SubjectPO subject = subjectDAO.findById(scope.getReferenceId()).orElse(null);
            if (subject != null && subjectName.equals(subject.getName())) {
                return subject.getId();
            }
        }
        throw new ToolExecuteException(ToolError.SCOPE_VIOLATION,
            "Subject " + subjectName + " not in service scope");
    }

    private Set<TableRef> buildAllowedTableRefs(List<McpServiceDataScope> scopeItems, String dsId) {
        return McpScopeResolution.frozenTableRefs(scopeItems, dsId);
    }

    /** Try to match tableRef against allowed via schema resolution or stripping. */
    private boolean tryResolve(TableRef ref, @Nullable String defaultSchema, Set<TableRef> allowed) {
        if (ref.schema() == null && defaultSchema != null
                && allowed.contains(new TableRef(defaultSchema, ref.name()))) {
            return true;
        }
        return ref.schema() != null && allowed.contains(new TableRef(null, ref.name()));
    }
}
