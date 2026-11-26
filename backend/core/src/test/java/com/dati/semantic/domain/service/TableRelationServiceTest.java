package com.dati.semantic.domain.service;

import com.dati.TestFixtures;
import com.dati.auth.authentication.User;
import com.dati.base.RequestContext;
import com.dati.base.exception.DatiException;
import com.dati.datasource.repository.dao.ColumnInfoDAO;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.model.TableRelation;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.dao.SubjectTableDAO;
import com.dati.semantic.repository.dao.TableRelationDAO;
import com.dati.semantic.repository.po.SubjectPO;
import com.dati.semantic.repository.po.SubjectTablePO;
import com.dati.semantic.repository.po.TableRelationPO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TableRelationService unit tests")
class TableRelationServiceTest {
    @Mock private TableRelationDAO tableRelationDAO;
    @Mock private SubjectDAO subjectDAO;
    @Mock private SubjectTableDAO subjectTableDAO;
    @Mock private ColumnInfoDAO columnInfoDAO;
    @Mock private PermissionService permissionService;
    @InjectMocks private TableRelationService service;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(TestFixtures.TEST_USER_ID);
        user.setName(TestFixtures.TEST_USER_ID);
        RequestContext.setUser(user);
        SubjectPO subject = new SubjectPO();
        subject.setId("subject-1");
        subject.setCreatedBy(TestFixtures.TEST_USER_ID);
        lenient().when(subjectDAO.findById("subject-1")).thenReturn(Optional.of(subject));
        lenient().doAnswer(inv -> null).when(permissionService).requireCurrentUser(any(), anyString(), any(), anyString());
        lenient().when(tableRelationDAO.save(any(TableRelationPO.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(columnInfoDAO.existsByTableIdAndName(anyString(), anyString())).thenReturn(true);
        lenient().when(subjectTableDAO.findBySubjectIdAndTableId(anyString(), anyString()))
            .thenAnswer(inv -> Optional.of(new SubjectTablePO()));
    }

    @AfterEach
    void tearDown() { RequestContext.getContext().clear(); }

    @Test
    @DisplayName("create stores a governed fact-to-dimension relation")
    void createStoresRelation() {
        TableRelation item = relation();
        TableRelation created = service.createRelation("subject-1", item);
        assertThat(created.getSubjectId()).isEqualTo("subject-1");
        assertThat(created.getCardinality()).isEqualTo("MANY_TO_ONE");
        assertThat(created.getRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("create rejects tables outside the subject")
    void createRejectsForeignTable() {
        when(subjectTableDAO.findBySubjectIdAndTableId("subject-1", "fact-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createRelation("subject-1", relation()))
            .isInstanceOf(DatiException.class);
    }

    @Test
    @DisplayName("create rejects a duplicate fact-to-dimension join")
    void createRejectsDuplicateJoin() {
        TableRelation item = relation();
        TableRelationPO existing = new TableRelationPO();
        existing.setId("rel-existing");
        when(tableRelationDAO.findBySubjectIdAndFactTableIdAndFactColumnAndDimensionTableIdAndDimensionColumn(
            "subject-1", "fact-1", "wo_id", "dim-1", "id")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createRelation("subject-1", item))
            .isInstanceOf(DatiException.class);
    }

    @Test
    @DisplayName("update increments revision and rejects unsupported cardinality")
    void updateValidatesCardinality() {
        TableRelationPO po = new TableRelationPO();
        po.setId("rel-1");
        po.setSubjectId("subject-1");
        po.setFactTableId("fact-1");
        po.setDimensionTableId("dim-1");
        po.setFactColumn("wo_id");
        po.setDimensionColumn("id");
        po.setCardinality("MANY_TO_ONE");
        po.setRevision(1);
        when(tableRelationDAO.findById("rel-1")).thenReturn(Optional.of(po));
        TableRelation patch = new TableRelation();
        patch.setCardinality("BAD");
        assertThatThrownBy(() -> service.updateRelation("rel-1", patch)).isInstanceOf(DatiException.class);
        patch.setCardinality("ONE_TO_ONE");
        TableRelation updated = service.updateRelation("rel-1", patch);
        assertThat(updated.getRevision()).isEqualTo(2);
    }

    private TableRelation relation() {
        TableRelation item = new TableRelation();
        item.setFactTableId("fact-1");
        item.setFactColumn("wo_id");
        item.setDimensionTableId("dim-1");
        item.setDimensionColumn("id");
        item.setOwner("owner");
        return item;
    }
}
