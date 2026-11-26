package com.dati.semantic.server.controller;

import com.dati.base.pojo.IdResponse;
import com.dati.base.pojo.PageReq;
import com.dati.base.pojo.PageResponse;
import com.dati.semantic.domain.model.Metric;
import com.dati.semantic.domain.service.MetricService;
import com.dati.semantic.server.assembler.MetricAssembler;
import com.dati.semantic.server.pojo.request.CreateMetricRequest;
import com.dati.semantic.server.pojo.request.UpdateMetricRequest;
import com.dati.semantic.server.pojo.vo.MetricVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MetricController {

    private final MetricService metricService;
    private final MetricAssembler metricAssembler;

    @PostMapping("/v1/subjects/{subjectId}/metrics")
    public IdResponse createMetric(@PathVariable String subjectId, @Valid @RequestBody CreateMetricRequest request) {
        Metric metric = new Metric();
        metric.setCode(request.getCode());
        metric.setName(request.getName());
        metric.setDescription(request.getDescription());
        metric.setAliases(request.getAliases());
        metric.setOwner(request.getOwner());
        metric.setGrain(request.getGrain());
        metric.setTimeField(request.getTimeField());
        metric.setTimeZone(request.getTimeZone());
        metric.setAllowedDimensions(request.getAllowedDimensions());
        metric.setSqlTemplate(request.getSqlTemplate());
        metric.setParameters(request.getParameters());
        metric.setTimeout(request.getTimeout());
        metric.setMaxRows(request.getMaxRows());
        metricAssembler.fillUsersFromRequest(metric);
        return new IdResponse(metricService.createMetric(subjectId, metric).getId());
    }

    @GetMapping("/v1/subjects/{subjectId}/metrics")
    public PageResponse<MetricVO> getMetrics(@PathVariable String subjectId, @Valid PageReq pageReq,
                                             @RequestParam(required = false) String keyword) {
        Sort sortBy = Sort.by(Sort.Direction.DESC, "updatedAt");
        return metricAssembler.toPageResponse(
            metricService.getMetrics(subjectId, keyword, pageReq.toPageRequest().withSort(sortBy)));
    }

    @GetMapping("/v1/metrics/{id}")
    public MetricVO getMetric(@PathVariable String id) {
        return metricAssembler.toVO(metricService.getMetricById(id));
    }

    @PutMapping("/v1/metrics/{id}")
    public IdResponse updateMetric(@PathVariable String id, @Valid @RequestBody UpdateMetricRequest request) {
        Metric metric = new Metric();
        metric.setName(request.getName());
        metric.setDescription(request.getDescription());
        metric.setAliases(request.getAliases());
        metric.setOwner(request.getOwner());
        metric.setGrain(request.getGrain());
        metric.setTimeField(request.getTimeField());
        metric.setTimeZone(request.getTimeZone());
        metric.setAllowedDimensions(request.getAllowedDimensions());
        metric.setSqlTemplate(request.getSqlTemplate());
        metric.setParameters(request.getParameters());
        metric.setTimeout(request.getTimeout());
        metric.setMaxRows(request.getMaxRows());
        metricAssembler.fillUpdateUserFromRequest(metric);
        metricService.updateMetric(id, metric);
        return new IdResponse(id);
    }

    @DeleteMapping("/v1/metrics/{id}")
    public IdResponse deleteMetric(@PathVariable String id) {
        metricService.deleteMetric(id);
        return new IdResponse(id);
    }
}
