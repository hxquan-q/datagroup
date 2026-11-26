package com.dati.mcp.domain.service;

import com.dati.datasource.repository.dao.ColumnInfoDAO;
import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.ColumnInfoPO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.mcp.domain.model.McpDataScopeType;
import com.dati.mcp.domain.model.McpResolvedTable;
import com.dati.mcp.domain.model.McpServiceDataScope;
import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.semantic.repository.dao.TableRelationDAO;
import com.dati.semantic.repository.po.TableRelationPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("McpTableRelationSnapshotService tests")
class McpTableRelationSnapshotServiceTest {

    @Mock
    private TableRelationDAO tableRelationDAO;
    @Mock
    private TableInfoDAO tableInfoDAO;
    @Mock
    private ColumnInfoDAO columnInfoDAO;

    private McpTableRelationSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new McpTableRelationSnapshotService(tableRelationDAO, tableInfoDAO, columnInfoDAO);
    }

    private TableInfoPO table(String id, String name) {
        TableInfoPO table = new TableInfoPO();
        table.setId(id);
        table.setDataSourceId("ds-1");
        table.setSchema("mes");
        table.setName(name);
        return table;
    }

    private ColumnInfoPO column(String tableId, String name) {
        ColumnInfoPO column = new ColumnInfoPO();
        column.setTableId(tableId);
        column.setName(name);
        return column;
    }

    private TableRelationPO relation() {
        TableRelationPO relation = new TableRelationPO();
        relation.setSubjectId("sub-1");
        relation.setFactTableId("fact-1");
        relation.setFactColumn("FMoID");
        relation.setDimensionTableId("dim-1");
        relation.setDimensionColumn("F_id");
        return relation;
    }

    private McpServiceDataScope scope(String... tables) {
        McpServiceDataScope scope = new McpServiceDataScope();
        scope.setScopeType(McpDataScopeType.SUBJECT);
        scope.setReferenceId("sub-1");
        scope.setResolvedTables(java.util.Arrays.stream(tables)
            .map(table -> new McpResolvedTable("ds-1", "mes", table)).toList());
        return scope;
    }

    private void mockMetadata() {
        TableInfoPO fact = table("fact-1", "t_pass");
        TableInfoPO dimension = table("dim-1", "t_mo");
        when(tableRelationDAO.findBySubjectId("sub-1")).thenReturn(List.of(relation()));
        when(tableInfoDAO.findAllById(any())).thenReturn(List.of(fact, dimension));
    }

    @Test
    @DisplayName("builds relation drafts with resolved table coordinates")
    void buildsRelationDrafts() {
        mockMetadata();
        when(columnInfoDAO.findByTableIdIn(any())).thenReturn(List.of(
            column("fact-1", "FMoID"), column("dim-1", "F_id")));

        List<McpServiceSnapshot.TableRelationDraft> result = service.buildRelationDrafts(
            List.of("sub-1"), List.of(scope("t_pass", "t_mo")));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().factTableName()).isEqualTo("t_pass");
        assertThat(result.getFirst().dimensionTableName()).isEqualTo("t_mo");
    }

    @Test
    @DisplayName("omits relation when a join column no longer exists")
    void omitsDeletedJoinColumn() {
        mockMetadata();
        when(columnInfoDAO.findByTableIdIn(any())).thenReturn(List.of(column("fact-1", "FMoID")));

        List<McpServiceSnapshot.TableRelationDraft> result = service.buildRelationDrafts(
            List.of("sub-1"), List.of(scope("t_pass", "t_mo")));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("omits relation outside frozen scope")
    void omitsRelationOutsideScope() {
        mockMetadata();
        when(columnInfoDAO.findByTableIdIn(any())).thenReturn(List.of(
            column("fact-1", "FMoID"), column("dim-1", "F_id")));

        List<McpServiceSnapshot.TableRelationDraft> result = service.buildRelationDrafts(
            List.of("sub-1"), List.of(scope("t_pass")));

        assertThat(result).isEmpty();
    }
}
