package com.dati.semantic.domain.service;

import com.dati.TestFixtures;
import com.dati.auth.authentication.User;
import com.dati.base.RequestContext;
import com.dati.base.exception.DatiException;
import com.dati.base.exception.ErrorCode;
import com.dati.datasource.repository.dao.ColumnInfoDAO;
import com.dati.datasource.repository.po.ColumnInfoPO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.permission.domain.model.Permission;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.SemanticEntityType;
import com.dati.semantic.domain.model.TableRelationRecommendation;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.dao.SubjectTableDAO;
import com.dati.semantic.repository.dao.TableRelationDAO;
import com.dati.semantic.repository.po.SemanticSearchDocument;
import com.dati.semantic.repository.po.SubjectPO;
import com.dati.semantic.repository.po.TableRelationPO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TableRelationRecommendationService unit tests")
class TableRelationRecommendationServiceTest {
    @Mock private SubjectDAO subjectDAO;
    @Mock private SubjectTableDAO subjectTableDAO;
    @Mock private TableRelationDAO tableRelationDAO;
    @Mock private ColumnInfoDAO columnInfoDAO;
    @Mock private SemanticIndexService semanticIndexService;
    @Mock private PermissionService permissionService;
    @InjectMocks private TableRelationRecommendationService service;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(TestFixtures.TEST_USER_ID);
        user.setName(TestFixtures.TEST_USER_ID);
        RequestContext.setUser(user);
        SubjectPO subject = new SubjectPO();
        subject.setId("subject-1");
        subject.setDatasourceId("ds-1");
        subject.setCreatedBy(TestFixtures.TEST_USER_ID);
        lenient().when(subjectDAO.findById("subject-1")).thenReturn(Optional.of(subject));
        lenient().doAnswer(inv -> null).when(permissionService).requireCurrentUser(any(), anyString(), any(), anyString());
        lenient().when(tableRelationDAO.findBySubjectId("subject-1")).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() { RequestContext.getContext().clear(); }

    @Test
    @DisplayName("recommends a fact-to-dimension relation from business tokens")
    void recommendsBusinessTokenRelation() {
        TableInfoPO fact = table("fact-1", "t_mes_passstaion", "过站记录");
        TableInfoPO dimension = table("dim-1", "t_mes_workshop", "车间");
        mockTables(fact, dimension);
        when(columnInfoDAO.findByTableId("fact-1")).thenReturn(List.of(
            column("fact-col-1", "fact-1", "FWorkShopID", "INT", "车间"),
            column("fact-col-2", "fact-1", "FQty", "DECIMAL", "数量")
        ));
        when(columnInfoDAO.findByTableId("dim-1")).thenReturn(List.of(
            column("dim-col-1", "dim-1", "F_id", "INT", ""),
            column("dim-col-2", "dim-1", "FName", "VARCHAR", "名称")
        ));

        List<TableRelationRecommendation> result = service.recommendRelations("subject-1", 20);

        assertThat(result).hasSize(1);
        TableRelationRecommendation candidate = result.getFirst();
        assertThat(candidate.getFactTableId()).isEqualTo("fact-1");
        assertThat(candidate.getFactColumn()).isEqualTo("FWorkShopID");
        assertThat(candidate.getDimensionTableId()).isEqualTo("dim-1");
        assertThat(candidate.getDimensionColumn()).isEqualTo("F_id");
        assertThat(candidate.getEvidence()).contains("TABLE_BUSINESS_TOKEN_MATCH", "TYPE_COMPATIBLE");
    }

    @Test
    @DisplayName("does not recommend an already governed relation")
    void skipsExistingRelation() {
        TableInfoPO fact = table("fact-1", "t_mes_passstaion", "过站记录");
        TableInfoPO dimension = table("dim-1", "t_mes_workshop", "车间");
        mockTables(fact, dimension);
        when(columnInfoDAO.findByTableId("fact-1")).thenReturn(List.of(column("fact-col-1", "fact-1", "FWorkShopID", "INT", "车间")));
        when(columnInfoDAO.findByTableId("dim-1")).thenReturn(List.of(column("dim-col-1", "dim-1", "F_id", "INT", "")));
        TableRelationPO existing = new TableRelationPO();
        existing.setFactTableId("fact-1");
        existing.setFactColumn("FWorkShopID");
        existing.setDimensionTableId("dim-1");
        existing.setDimensionColumn("F_id");
        when(tableRelationDAO.findBySubjectId("subject-1")).thenReturn(List.of(existing));

        assertThat(service.recommendRelations("subject-1", 20)).isEmpty();
    }

    @Test
    @DisplayName("does not recommend a case-variant of an existing governed relation")
    void skipsExistingCaseVariantRelation() {
        TableInfoPO fact = table("fact-1", "t_mes_passstaion", "过站记录");
        TableInfoPO dimension = table("dim-1", "t_mes_workshop", "车间");
        mockTables(fact, dimension);
        when(columnInfoDAO.findByTableId("fact-1")).thenReturn(List.of(column("fact-col-1", "fact-1", "FWorkShopID", "INT", "车间")));
        when(columnInfoDAO.findByTableId("dim-1")).thenReturn(List.of(column("dim-col-1", "dim-1", "F_id", "INT", "")));
        TableRelationPO existing = new TableRelationPO();
        existing.setFactTableId("fact-1");
        existing.setFactColumn("fworkshopid");
        existing.setDimensionTableId("dim-1");
        existing.setDimensionColumn("f_ID");
        when(tableRelationDAO.findBySubjectId("subject-1")).thenReturn(List.of(existing));

        assertThat(service.recommendRelations("subject-1", 20)).isEmpty();
    }

    @Test
    @DisplayName("uses extracted dictionary values as supporting evidence")
    void usesDictionaryValueOverlap() {
        TableInfoPO fact = table("fact-1", "t_mes_passstaion", "过站记录");
        TableInfoPO dimension = table("dim-1", "t_mes_workshop", "车间");
        mockTables(fact, dimension);
        when(columnInfoDAO.findByTableId("fact-1")).thenReturn(List.of(column("fact-col-1", "fact-1", "FWorkShopID", "INT", "车间")));
        when(columnInfoDAO.findByTableId("dim-1")).thenReturn(List.of(column("dim-col-1", "dim-1", "F_id", "INT", "")));
        when(semanticIndexService.findByTableFieldAndType("fact-1", "FWorkShopID", SemanticEntityType.FIELD_VALUE))
            .thenReturn(List.of(valueDoc("42"), valueDoc("43")));
        when(semanticIndexService.findByTableFieldAndType("dim-1", "F_id", SemanticEntityType.FIELD_VALUE))
            .thenReturn(List.of(valueDoc("42"), valueDoc("43"), valueDoc("44")));

        List<TableRelationRecommendation> result = service.recommendRelations("subject-1", 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEvidence()).contains("VALUE_OVERLAP");
    }

    @Test
    @DisplayName("does not use a shared column name without table-level semantic evidence")
    void rejectsSharedColumnOnUnrelatedTables() {
        TableInfoPO fact = table("fact-1", "t_delivery_detail", "送货明细");
        TableInfoPO dimension = table("dim-1", "t_iqc_check", "IQC检验单");
        mockTables(fact, dimension);
        when(columnInfoDAO.findByTableId("fact-1")).thenReturn(List.of(column("fact-col-1", "fact-1", "FMaterialID", "INT", "物料编码")));
        when(columnInfoDAO.findByTableId("dim-1")).thenReturn(List.of(column("dim-col-1", "dim-1", "FMaterialID", "INT", "物料编码")));

        assertThat(service.recommendRelations("subject-1", 20)).isEmpty();
    }

    @Test
    @DisplayName("caps confidence when the dimension join column is not a primary key")
    void capsConfidenceForBusinessKeyMatch() {
        TableInfoPO fact = table("fact-1", "t_delivery", "送货单");
        TableInfoPO dimension = table("dim-1", "t_mo", "工单号");
        mockTables(fact, dimension);
        ColumnInfoPO factColumn = column("fact-col-1", "fact-1", "FBillNo", "VARCHAR", "工单编号");
        factColumn.setAliases(List.of("工单编号", "工单号"));
        ColumnInfoPO dimensionColumn = column("dim-col-1", "dim-1", "FBillNo", "VARCHAR", "工单编号");
        dimensionColumn.setAliases(List.of("工单编号", "工单号"));
        when(columnInfoDAO.findByTableId("fact-1")).thenReturn(List.of(factColumn));
        when(columnInfoDAO.findByTableId("dim-1")).thenReturn(List.of(dimensionColumn));

        List<TableRelationRecommendation> result = service.recommendRelations("subject-1", 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getScore()).isLessThan(85);
        assertThat(result.getFirst().getConfidence()).isNotEqualTo("HIGH");
        assertThat(result.getFirst().getCardinality()).isEqualTo("MANY_TO_MANY");
    }

    @Test
    @DisplayName("uses dictionary overlap only with data source view permission")
    void hidesDictionaryOverlapWithoutDataSourceView() {
        doThrow(new DatiException(ErrorCode.PERMISSION_DENIED)).when(permissionService).requireDataSource("ds-1", Permission.VIEW);
        TableInfoPO fact = table("fact-1", "t_mes_passstaion", "过站记录");
        TableInfoPO dimension = table("dim-1", "t_mes_workshop", "车间");
        mockTables(fact, dimension);
        when(columnInfoDAO.findByTableId("fact-1")).thenReturn(List.of(column("fact-col-1", "fact-1", "FWorkShopID", "INT", "车间")));
        when(columnInfoDAO.findByTableId("dim-1")).thenReturn(List.of(column("dim-col-1", "dim-1", "F_id", "INT", "")));

        List<TableRelationRecommendation> result = service.recommendRelations("subject-1", 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEvidence()).doesNotContain("VALUE_OVERLAP");
        verifyNoInteractions(semanticIndexService);
    }

    @Test
    @DisplayName("dictionary overlap can qualify a borderline table-level match")
    void dictionaryOverlapRaisesBorderlineMatch() {
        TableInfoPO fact = table("fact-1", "t_fact", "事实");
        TableInfoPO dimension = table("dim-1", "t_dimension", "workshop");
        mockTables(fact, dimension);
        when(columnInfoDAO.findByTableId("fact-1")).thenReturn(List.of(column("fact-col-1", "fact-1", "FWorkShopID", "INT", "")));
        when(columnInfoDAO.findByTableId("dim-1")).thenReturn(List.of(column("dim-col-1", "dim-1", "F_id", "INT", "")));
        when(semanticIndexService.findByTableFieldAndType("fact-1", "FWorkShopID", SemanticEntityType.FIELD_VALUE))
            .thenReturn(List.of(valueDoc("42"), valueDoc("43")));
        when(semanticIndexService.findByTableFieldAndType("dim-1", "F_id", SemanticEntityType.FIELD_VALUE))
            .thenReturn(List.of(valueDoc("42"), valueDoc("43")));

        List<TableRelationRecommendation> result = service.recommendRelations("subject-1", 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEvidence()).contains("VALUE_OVERLAP");
    }

    @Test
    @DisplayName("does not treat PostgreSQL uuid and varchar keys as compatible")
    void rejectsUuidVarcharMismatch() {
        TableInfoPO fact = table("fact-1", "t_order", "订单");
        TableInfoPO dimension = table("dim-1", "t_sys_user", "用户");
        mockTables(fact, dimension);
        when(columnInfoDAO.findByTableId("fact-1")).thenReturn(List.of(column("fact-col-1", "fact-1", "FUserID", "UUID", "")));
        when(columnInfoDAO.findByTableId("dim-1")).thenReturn(List.of(column("dim-col-1", "dim-1", "FUserID", "VARCHAR", "")));

        assertThat(service.recommendRelations("subject-1", 20)).isEmpty();
    }

    private void mockTables(TableInfoPO... tables) {
        when(subjectTableDAO.findTablesBySubjectId(eq("subject-1"), any(Pageable.class)))
            .thenAnswer(inv -> new PageImpl<>(List.of(tables), inv.getArgument(1), tables.length));
    }

    private TableInfoPO table(String id, String name, String alias) {
        TableInfoPO table = new TableInfoPO();
        table.setId(id);
        table.setName(name);
        table.setAliases(List.of(alias));
        return table;
    }

    private ColumnInfoPO column(String id, String tableId, String name, String type, String alias) {
        ColumnInfoPO column = new ColumnInfoPO();
        column.setId(id);
        column.setTableId(tableId);
        column.setName(name);
        column.setColumnType(type);
        column.setAliases(alias.isBlank() ? List.of() : List.of(alias));
        return column;
    }

    private SemanticSearchDocument valueDoc(String value) {
        return SemanticSearchDocument.builder().keywords(List.of(value)).build();
    }
}
