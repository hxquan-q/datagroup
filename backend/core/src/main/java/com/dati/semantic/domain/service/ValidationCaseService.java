package com.dati.semantic.domain.service;

import com.dati.base.exception.DatiException;
import com.dati.base.exception.ErrorCode;
import com.dati.permission.domain.model.Permission;
import com.dati.permission.domain.model.ResourceType;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.model.ValidationCase;
import com.dati.semantic.repository.dao.MetricDAO;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.dao.ValidationCaseDAO;
import com.dati.semantic.repository.mapper.ValidationCaseMapper;
import com.dati.semantic.repository.po.MetricPO;
import com.dati.semantic.repository.po.SubjectPO;
import com.dati.semantic.repository.po.ValidationCasePO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidationCaseService {
    private final ValidationCaseDAO validationCaseDAO;
    private final SubjectDAO subjectDAO;
    private final MetricDAO metricDAO;
    private final PermissionService permissionService;

    public ValidationCaseService(ValidationCaseDAO validationCaseDAO, SubjectDAO subjectDAO,
                                 MetricDAO metricDAO, PermissionService permissionService) {
        this.validationCaseDAO = validationCaseDAO;
        this.subjectDAO = subjectDAO;
        this.metricDAO = metricDAO;
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
