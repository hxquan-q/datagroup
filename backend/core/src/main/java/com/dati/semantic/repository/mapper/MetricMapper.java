package com.dati.semantic.repository.mapper;

import com.dati.base.MapperUtils;
import com.dati.semantic.domain.model.Metric;
import com.dati.semantic.repository.po.MetricPO;

import java.util.ArrayList;

public class MetricMapper {

    public static MetricPO toPO(Metric metric) {
        MetricPO po = new MetricPO();
        MapperUtils.copyBaseResourceInfo(metric, po);
        copyFields(metric, po);
        return po;
    }

    public static void copyFields(Metric metric, MetricPO po) {
        po.setSubjectId(metric.getSubjectId());
        po.setDataSourceId(metric.getDataSourceId());
        po.setCode(metric.getCode());
        po.setAliases(metric.getAliases() != null ? metric.getAliases() : new ArrayList<>());
        po.setOwner(metric.getOwner());
        po.setGrain(metric.getGrain());
        po.setTimeField(metric.getTimeField());
        po.setTimeZone(metric.getTimeZone());
        po.setAllowedDimensions(metric.getAllowedDimensions() != null ? metric.getAllowedDimensions() : new ArrayList<>());
        po.setSqlTemplate(metric.getSqlTemplate());
        po.setParameters(metric.getParameters() != null ? metric.getParameters() : new ArrayList<>());
        po.setTimeout(metric.getTimeout());
        po.setMaxRows(metric.getMaxRows());
        po.setRevision(metric.getRevision());
    }

    public static Metric toMetric(MetricPO po) {
        Metric metric = new Metric();
        MapperUtils.copyBaseResourceInfo(po, metric);
        metric.setSubjectId(po.getSubjectId());
        metric.setDataSourceId(po.getDataSourceId());
        metric.setCode(po.getCode());
        metric.setAliases(po.getAliases() != null ? po.getAliases() : new ArrayList<>());
        metric.setOwner(po.getOwner());
        metric.setGrain(po.getGrain());
        metric.setTimeField(po.getTimeField());
        metric.setTimeZone(po.getTimeZone());
        metric.setAllowedDimensions(po.getAllowedDimensions() != null ? po.getAllowedDimensions() : new ArrayList<>());
        metric.setSqlTemplate(po.getSqlTemplate());
        metric.setParameters(po.getParameters() != null ? po.getParameters() : new ArrayList<>());
        metric.setTimeout(po.getTimeout());
        metric.setMaxRows(po.getMaxRows());
        metric.setRevision(po.getRevision());
        return metric;
    }
}
