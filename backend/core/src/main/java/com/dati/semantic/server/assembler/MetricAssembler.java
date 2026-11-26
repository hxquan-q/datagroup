package com.dati.semantic.server.assembler;

import com.dati.base.BaseAssembler;
import com.dati.base.pojo.PageResponse;
import com.dati.semantic.domain.model.Metric;
import com.dati.semantic.server.pojo.vo.MetricVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MetricAssembler extends BaseAssembler {

    public MetricVO toVO(Metric metric) {
        if (metric == null) return null;
        MetricVO vo = mapFields(metric);
        super.fillUserInfo(List.of(vo));
        return vo;
    }

    public PageResponse<MetricVO> toPageResponse(Page<Metric> page) {
        List<MetricVO> vos = page.getContent().stream().map(this::mapFields).collect(Collectors.toList());
        super.fillUserInfo(vos);
        return PageResponse.of(new PageImpl<>(vos, page.getPageable(), page.getTotalElements()));
    }

    private MetricVO mapFields(Metric metric) {
        MetricVO vo = new MetricVO();
        super.copyBaseInfo(metric, vo);
        vo.setSubjectId(metric.getSubjectId());
        vo.setDataSourceId(metric.getDataSourceId());
        vo.setCode(metric.getCode());
        vo.setAliases(metric.getAliases() != null ? metric.getAliases() : new ArrayList<>());
        vo.setOwner(metric.getOwner());
        vo.setGrain(metric.getGrain());
        vo.setTimeField(metric.getTimeField());
        vo.setTimeZone(metric.getTimeZone());
        vo.setAllowedDimensions(metric.getAllowedDimensions() != null ? metric.getAllowedDimensions() : new ArrayList<>());
        vo.setSqlTemplate(metric.getSqlTemplate());
        vo.setParameters(metric.getParameters() != null ? metric.getParameters() : new ArrayList<>());
        vo.setTimeout(metric.getTimeout());
        vo.setMaxRows(metric.getMaxRows());
        vo.setRevision(metric.getRevision());
        return vo;
    }
}
