package com.dati.semantic.domain.service;

import com.dati.base.exception.DatiException;
import com.dati.base.exception.ErrorCode;
import com.dati.datasource.domain.model.ColumnInfo;
import com.dati.datasource.repository.dao.ColumnInfoDAO;
import com.dati.datasource.repository.mapper.ColumnMapper;
import com.dati.permission.domain.model.Permission;
import com.dati.permission.domain.model.ResourceType;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.model.TableRelation;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.dao.SubjectTableDAO;
import com.dati.semantic.repository.dao.TableRelationDAO;
import com.dati.semantic.repository.mapper.TableRelationMapper;
import com.dati.semantic.repository.po.SubjectPO;
import com.dati.semantic.repository.po.TableRelationPO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class TableRelationService {
    private static final Set<String> CARDINALITIES = Set.of("MANY_TO_ONE", "ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_MANY");
    private final TableRelationDAO tableRelationDAO;
    private final SubjectDAO subjectDAO;
    private final SubjectTableDAO subjectTableDAO;
    private final ColumnInfoDAO columnInfoDAO;
    private final PermissionService permissionService;

    public TableRelationService(TableRelationDAO tableRelationDAO, SubjectDAO subjectDAO,
                                SubjectTableDAO subjectTableDAO, ColumnInfoDAO columnInfoDAO, PermissionService permissionService) {
        this.tableRelationDAO = tableRelationDAO;
        this.subjectDAO = subjectDAO;
        this.subjectTableDAO = subjectTableDAO;
        this.columnInfoDAO = columnInfoDAO;
        this.permissionService = permissionService;
    }

    @Transactional
    public TableRelation createRelation(String subjectId, TableRelation item) {
        SubjectPO subject = requireSubject(subjectId);
        permissionService.requireCurrentUser(ResourceType.SUBJECT, subjectId, Permission.EDIT, subject.getCreatedBy());
        validate(item);
        requireTableInSubject(subjectId, item.getFactTableId());
        requireTableInSubject(subjectId, item.getDimensionTableId());
        requireColumn(item.getFactTableId(), item.getFactColumn());
        requireColumn(item.getDimensionTableId(), item.getDimensionColumn());
        item.setSubjectId(subjectId);
        item.setRevision(1);
        if (item.getCardinality() == null || item.getCardinality().isBlank()) item.setCardinality("MANY_TO_ONE");
        item.setName(item.getFactColumn() + " -> " + item.getDimensionColumn());
        return TableRelationMapper.toModel(tableRelationDAO.save(TableRelationMapper.toPO(item)));
    }

    @Transactional
    public TableRelation updateRelation(String id, TableRelation patch) {
        TableRelationPO po = requireRelation(id);
        SubjectPO subject = requireSubject(po.getSubjectId());
        permissionService.requireCurrentUser(ResourceType.SUBJECT, po.getSubjectId(), Permission.EDIT, subject.getCreatedBy());
        if (patch.getFactColumn() != null) {
            if (patch.getFactColumn().isBlank()) throw new DatiException(ErrorCode.INVALID_PARAMETER, "factColumn must not be blank");
            if (!patch.getFactColumn().equals(po.getFactColumn())) {
                requireColumn(po.getFactTableId(), patch.getFactColumn());
            }
            po.setFactColumn(patch.getFactColumn());
        }
        if (patch.getDimensionColumn() != null) {
            if (patch.getDimensionColumn().isBlank()) throw new DatiException(ErrorCode.INVALID_PARAMETER, "dimensionColumn must not be blank");
            if (!patch.getDimensionColumn().equals(po.getDimensionColumn())) {
                requireColumn(po.getDimensionTableId(), patch.getDimensionColumn());
            }
            po.setDimensionColumn(patch.getDimensionColumn());
        }
        if (patch.getCardinality() != null) {
            validateCardinality(patch.getCardinality());
            po.setCardinality(patch.getCardinality());
        }
        if (patch.getOwner() != null) po.setOwner(patch.getOwner());
        if (patch.getDescription() != null) po.setDescription(patch.getDescription());
        if (patch.getUpdatedBy() != null) po.setUpdatedBy(patch.getUpdatedBy());
        po.setName(po.getFactColumn() + " -> " + po.getDimensionColumn());
        po.setRevision((po.getRevision() == null ? 1 : po.getRevision()) + 1);
        return TableRelationMapper.toModel(tableRelationDAO.save(po));
    }

    @Transactional(readOnly = true)
    public Page<TableRelation> getRelations(String subjectId, Pageable pageable) {
        SubjectPO subject = requireSubject(subjectId);
        permissionService.requireCurrentUser(ResourceType.SUBJECT, subjectId, Permission.VIEW, subject.getCreatedBy());
        return tableRelationDAO.findBySubjectId(subjectId, pageable).map(TableRelationMapper::toModel);
    }

    @Transactional(readOnly = true)
    public List<ColumnInfo> getRelationColumns(String subjectId, String tableId) {
        SubjectPO subject = requireSubject(subjectId);
        permissionService.requireCurrentUser(ResourceType.SUBJECT, subjectId, Permission.EDIT, subject.getCreatedBy());
        requireTableInSubject(subjectId, tableId);
        return columnInfoDAO.findByTableId(tableId).stream().map(ColumnMapper::toColumnInfo).toList();
    }

    @Transactional
    public void deleteRelation(String id) {
        TableRelationPO po = requireRelation(id);
        SubjectPO subject = requireSubject(po.getSubjectId());
        permissionService.requireCurrentUser(ResourceType.SUBJECT, po.getSubjectId(), Permission.EDIT, subject.getCreatedBy());
        tableRelationDAO.delete(po);
    }

    private TableRelationPO requireRelation(String id) {
        return tableRelationDAO.findById(id)
            .orElseThrow(() -> new DatiException(ErrorCode.INVALID_PARAMETER, "table relation not found: " + id));
    }

    private SubjectPO requireSubject(String subjectId) {
        return subjectDAO.findById(subjectId)
            .orElseThrow(() -> new DatiException(ErrorCode.SM_SUBJECT_NOT_FOUND, subjectId));
    }

    private void requireColumn(String tableId, String column) {
        if (!columnInfoDAO.existsByTableIdAndName(tableId, column)) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "column does not exist in table: " + column);
        }
    }

    private void requireTableInSubject(String subjectId, String tableId) {
        subjectTableDAO.findBySubjectIdAndTableId(subjectId, tableId)
            .orElseThrow(() -> new DatiException(ErrorCode.SM_TABLE_NOT_IN_SUBJECT, tableId, subjectId));
    }

    private void validate(TableRelation item) {
        if (item.getFactTableId() == null || item.getFactTableId().isBlank()
            || item.getDimensionTableId() == null || item.getDimensionTableId().isBlank()
            || item.getFactColumn() == null || item.getFactColumn().isBlank()
            || item.getDimensionColumn() == null || item.getDimensionColumn().isBlank()) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "fact/dimension table and columns are required");
        }
        if (item.getCardinality() != null && !item.getCardinality().isBlank()) validateCardinality(item.getCardinality());
    }

    private void validateCardinality(String cardinality) {
        if (!CARDINALITIES.contains(cardinality)) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "unsupported cardinality: " + cardinality);
        }
    }
}
