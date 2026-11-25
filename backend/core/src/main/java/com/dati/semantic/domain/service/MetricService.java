package com.dati.semantic.domain.service;

import com.dati.base.exception.DatiException;
import com.dati.base.exception.ErrorCode;
import com.dati.common.StringUtils;
import com.dati.permission.domain.model.Permission;
import com.dati.permission.domain.model.ResourceType;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.model.Metric;
import com.dati.semantic.repository.dao.MetricDAO;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.mapper.MetricMapper;
import com.dati.semantic.repository.po.MetricPO;
import com.dati.semantic.repository.po.SubjectPO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class MetricService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,127}$");

    private final MetricDAO metricDAO;
    private final SubjectDAO subjectDAO;
    private final PermissionService permissionService;

    public MetricService(MetricDAO metricDAO, SubjectDAO subjectDAO, PermissionService permissionService) {
        this.metricDAO = metricDAO;
        this.subjectDAO = subjectDAO;
        this.permissionService = permissionService;
    }

    @Transactional
    public Metric createMetric(String subjectId, Metric metric) {
        SubjectPO subject = requireSubject(subjectId);
        permissionService.requireCurrentUser(ResourceType.SUBJECT, subjectId, Permission.EDIT, subject.getCreatedBy());
        validateForCreate(metric);
        metricDAO.findBySubjectIdAndCode(subjectId, metric.getCode()).ifPresent(existing -> {
            throw new DatiException(ErrorCode.SM_METRIC_CODE_EXISTS, metric.getCode());
        });

        metric.setSubjectId(subjectId);
        metric.setDataSourceId(subject.getDatasourceId());
        metric.setRevision(1);
        MetricPO saved = metricDAO.save(MetricMapper.toPO(metric));
        return MetricMapper.toMetric(saved);
    }

    @Transactional
    public Metric updateMetric(String id, Metric patch) {
        MetricPO po = metricDAO.findById(id)
            .orElseThrow(() -> new DatiException(ErrorCode.SM_METRIC_NOT_FOUND, id));
        SubjectPO subject = requireSubject(po.getSubjectId());
        permissionService.requireCurrentUser(ResourceType.SUBJECT, po.getSubjectId(), Permission.EDIT, subject.getCreatedBy());

        if (patch.getName() != null) po.setName(patch.getName());
        if (patch.getDescription() != null) po.setDescription(patch.getDescription());
        if (patch.getAliases() != null) po.setAliases(patch.getAliases());
        if (patch.getOwner() != null) po.setOwner(patch.getOwner());
        if (patch.getGrain() != null) po.setGrain(patch.getGrain());
        if (patch.getTimeField() != null) po.setTimeField(patch.getTimeField());
        if (patch.getTimeZone() != null) po.setTimeZone(patch.getTimeZone());
        if (patch.getAllowedDimensions() != null) po.setAllowedDimensions(patch.getAllowedDimensions());
        if (patch.getSqlTemplate() != null) {
            if (patch.getSqlTemplate().isBlank()) {
                throw new DatiException(ErrorCode.INVALID_PARAMETER, "sqlTemplate must not be blank");
            }
            po.setSqlTemplate(patch.getSqlTemplate());
        }
        if (patch.getParameters() != null) po.setParameters(patch.getParameters());
        if (patch.getTimeout() != null) po.setTimeout(patch.getTimeout());
        if (patch.getMaxRows() != null) po.setMaxRows(patch.getMaxRows());
        if (patch.getUpdatedBy() != null) po.setUpdatedBy(patch.getUpdatedBy());
        validateBudget(po.getTimeout(), po.getMaxRows());
        po.setRevision((po.getRevision() == null ? 1 : po.getRevision()) + 1);
        return MetricMapper.toMetric(metricDAO.save(po));
    }

    @Transactional
    public void deleteMetric(String id) {
        MetricPO po = metricDAO.findById(id)
            .orElseThrow(() -> new DatiException(ErrorCode.SM_METRIC_NOT_FOUND, id));
        SubjectPO subject = requireSubject(po.getSubjectId());
        permissionService.requireCurrentUser(ResourceType.SUBJECT, po.getSubjectId(), Permission.EDIT, subject.getCreatedBy());
        metricDAO.delete(po);
    }

    @Transactional(readOnly = true)
    public Metric getMetricById(String id) {
        MetricPO po = metricDAO.findById(id)
            .orElseThrow(() -> new DatiException(ErrorCode.SM_METRIC_NOT_FOUND, id));
        SubjectPO subject = requireSubject(po.getSubjectId());
        permissionService.requireCurrentUser(ResourceType.SUBJECT, po.getSubjectId(), Permission.VIEW, subject.getCreatedBy());
        return MetricMapper.toMetric(po);
    }

    @Transactional(readOnly = true)
    public Page<Metric> getMetrics(String subjectId, String keyword, Pageable pageable) {
        SubjectPO subject = requireSubject(subjectId);
        permissionService.requireCurrentUser(ResourceType.SUBJECT, subjectId, Permission.VIEW, subject.getCreatedBy());
        Page<MetricPO> pos = StringUtils.isEmpty(keyword)
            ? metricDAO.findBySubjectId(subjectId, pageable)
            : metricDAO.findBySubjectIdAndKeyword(subjectId, keyword, pageable);
        return pos.map(MetricMapper::toMetric);
    }

    @Transactional(readOnly = true)
    public List<Metric> getMetricsBySubjectIds(Collection<String> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return List.of();
        }
        return metricDAO.findBySubjectIdIn(subjectIds).stream().map(MetricMapper::toMetric).toList();
    }

    private SubjectPO requireSubject(String subjectId) {
        return subjectDAO.findById(subjectId)
            .orElseThrow(() -> new DatiException(ErrorCode.SM_SUBJECT_NOT_FOUND, subjectId));
    }

    private void validateForCreate(Metric metric) {
        if (metric.getCode() == null || !CODE_PATTERN.matcher(metric.getCode()).matches()) {
            throw new DatiException(ErrorCode.SM_METRIC_CODE_INVALID, metric.getCode());
        }
        if (metric.getSqlTemplate() == null || metric.getSqlTemplate().isBlank()) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "sqlTemplate is required");
        }
        validateBudget(metric.getTimeout(), metric.getMaxRows());
    }

    private void validateBudget(Integer timeout, Integer maxRows) {
        if (timeout == null || timeout < 1 || timeout > 300) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "timeout must be between 1 and 300 seconds");
        }
        if (maxRows == null || maxRows < 1 || maxRows > 10000) {
            throw new DatiException(ErrorCode.INVALID_PARAMETER, "maxRows must be between 1 and 10000");
        }
    }
}
