package com.dati.semantic.repository.mapper;

import com.dati.base.MapperUtils;
import com.dati.semantic.domain.model.ValidationCase;
import com.dati.semantic.repository.po.ValidationCasePO;

import java.util.ArrayList;
import java.util.HashMap;

public class ValidationCaseMapper {
    public static ValidationCasePO toPO(ValidationCase item) {
        ValidationCasePO po = new ValidationCasePO();
        MapperUtils.copyBaseResourceInfo(item, po);
        copyFields(item, po);
        return po;
    }
    public static void copyFields(ValidationCase item, ValidationCasePO po) {
        po.setSubjectId(item.getSubjectId());
        po.setMetricId(item.getMetricId());
        po.setQuestion(item.getQuestion());
        po.setArguments(item.getArguments() != null ? item.getArguments() : new HashMap<>());
        po.setDimensions(item.getDimensions() != null ? item.getDimensions() : new ArrayList<>());
        po.setAssertionType(item.getAssertionType());
        po.setExpectedValue(item.getExpectedValue());
        po.setOwner(item.getOwner());
        po.setEnabled(item.getEnabled());
        po.setLastStatus(item.getLastStatus());
        po.setLastMessage(item.getLastMessage());
        po.setLastRunAt(item.getLastRunAt());
        po.setLastDurationMs(item.getLastDurationMs());
        po.setLastMetricRevision(item.getLastMetricRevision());
    }
    public static ValidationCase toModel(ValidationCasePO po) {
        ValidationCase item = new ValidationCase();
        MapperUtils.copyBaseResourceInfo(po, item);
        item.setSubjectId(po.getSubjectId());
        item.setMetricId(po.getMetricId());
        item.setQuestion(po.getQuestion());
        item.setArguments(po.getArguments() != null ? po.getArguments() : new HashMap<>());
        item.setDimensions(po.getDimensions() != null ? po.getDimensions() : new ArrayList<>());
        item.setAssertionType(po.getAssertionType());
        item.setExpectedValue(po.getExpectedValue());
        item.setOwner(po.getOwner());
        item.setEnabled(po.getEnabled());
        item.setLastStatus(po.getLastStatus());
        item.setLastMessage(po.getLastMessage());
        item.setLastRunAt(po.getLastRunAt());
        item.setLastDurationMs(po.getLastDurationMs());
        item.setLastMetricRevision(po.getLastMetricRevision());
        return item;
    }
}
