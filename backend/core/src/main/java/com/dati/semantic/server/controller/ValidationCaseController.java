package com.dati.semantic.server.controller;

import com.dati.base.pojo.IdResponse;
import com.dati.base.pojo.PageReq;
import com.dati.base.pojo.PageResponse;
import com.dati.semantic.domain.model.ValidationCase;
import com.dati.semantic.domain.service.ValidationCaseService;
import com.dati.semantic.server.assembler.ValidationCaseAssembler;
import com.dati.semantic.server.pojo.request.CreateValidationCaseRequest;
import com.dati.semantic.server.pojo.request.UpdateValidationCaseRequest;
import com.dati.semantic.server.pojo.vo.ValidationCaseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ValidationCaseController {
    private final ValidationCaseService service;
    private final ValidationCaseAssembler assembler;

    @PostMapping("/v1/subjects/{subjectId}/validation-cases")
    public IdResponse create(@PathVariable String subjectId, @Valid @RequestBody CreateValidationCaseRequest request) {
        ValidationCase item = new ValidationCase();
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setMetricId(request.getMetricId());
        item.setQuestion(request.getQuestion());
        item.setArguments(request.getArguments());
        item.setDimensions(request.getDimensions());
        item.setAssertionType(request.getAssertionType());
        item.setExpectedValue(request.getExpectedValue());
        item.setOwner(request.getOwner());
        item.setEnabled(request.getEnabled());
        assembler.fillUsersFromRequest(item);
        return new IdResponse(service.createCase(subjectId, item).getId());
    }

    @GetMapping("/v1/subjects/{subjectId}/validation-cases")
    public PageResponse<ValidationCaseVO> list(@PathVariable String subjectId, @Valid PageReq pageReq) {
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        return assembler.toPageResponse(service.getCases(subjectId, pageReq.toPageRequest().withSort(sort)));
    }

    @GetMapping("/v1/validation-cases/{id}")
    public ValidationCaseVO get(@PathVariable String id) {
        return assembler.toVO(service.getCase(id));
    }

    @PutMapping("/v1/validation-cases/{id}")
    public IdResponse update(@PathVariable String id, @Valid @RequestBody UpdateValidationCaseRequest request) {
        ValidationCase item = new ValidationCase();
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setQuestion(request.getQuestion());
        item.setArguments(request.getArguments());
        item.setDimensions(request.getDimensions());
        item.setAssertionType(request.getAssertionType());
        item.setExpectedValue(request.getExpectedValue());
        item.setOwner(request.getOwner());
        item.setEnabled(request.getEnabled());
        assembler.fillUpdateUserFromRequest(item);
        service.updateCase(id, item);
        return new IdResponse(id);
    }

    @PostMapping("/v1/validation-cases/{id}/run")
    public ValidationCaseVO run(@PathVariable String id) {
        return assembler.toVO(service.runCase(id));
    }

    @DeleteMapping("/v1/validation-cases/{id}")
    public IdResponse delete(@PathVariable String id) {
        service.deleteCase(id);
        return new IdResponse(id);
    }
}
