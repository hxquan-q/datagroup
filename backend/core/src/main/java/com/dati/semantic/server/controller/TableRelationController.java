package com.dati.semantic.server.controller;

import com.dati.base.pojo.IdResponse;
import com.dati.base.pojo.PageReq;
import com.dati.base.pojo.PageResponse;
import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.semantic.domain.model.TableRelation;
import com.dati.semantic.domain.service.TableRelationService;
import com.dati.semantic.server.assembler.TableRelationAssembler;
import com.dati.semantic.server.pojo.request.CreateTableRelationRequest;
import com.dati.semantic.server.pojo.request.UpdateTableRelationRequest;
import com.dati.semantic.server.pojo.vo.TableRelationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class TableRelationController {
    private final TableRelationService service;
    private final TableRelationAssembler assembler;
    private final TableInfoDAO tableInfoDAO;

    @PostMapping("/v1/subjects/{subjectId}/relations")
    public IdResponse create(@PathVariable String subjectId, @Valid @RequestBody CreateTableRelationRequest request) {
        TableRelation item = new TableRelation();
        item.setFactTableId(request.getFactTableId());
        item.setFactColumn(request.getFactColumn());
        item.setDimensionTableId(request.getDimensionTableId());
        item.setDimensionColumn(request.getDimensionColumn());
        item.setCardinality(request.getCardinality());
        item.setOwner(request.getOwner());
        item.setDescription(request.getDescription());
        assembler.fillUsersFromRequest(item);
        return new IdResponse(service.createRelation(subjectId, item).getId());
    }

    @GetMapping("/v1/subjects/{subjectId}/relations")
    public PageResponse<TableRelationVO> list(@PathVariable String subjectId, @Valid PageReq pageReq) {
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        var page = service.getRelations(subjectId, pageReq.toPageRequest().withSort(sort));
        return assembler.toPageResponse(page, tableNames(page.getContent()));
    }

    @PutMapping("/v1/relations/{id}")
    public IdResponse update(@PathVariable String id, @Valid @RequestBody UpdateTableRelationRequest request) {
        TableRelation item = new TableRelation();
        item.setFactColumn(request.getFactColumn());
        item.setDimensionColumn(request.getDimensionColumn());
        item.setCardinality(request.getCardinality());
        item.setOwner(request.getOwner());
        item.setDescription(request.getDescription());
        assembler.fillUpdateUserFromRequest(item);
        service.updateRelation(id, item);
        return new IdResponse(id);
    }

    @DeleteMapping("/v1/relations/{id}")
    public IdResponse delete(@PathVariable String id) {
        service.deleteRelation(id);
        return new IdResponse(id);
    }

    private Map<String, String> tableNames(List<TableRelation> relations) {
        List<String> ids = relations.stream()
            .flatMap(item -> java.util.stream.Stream.of(item.getFactTableId(), item.getDimensionTableId()))
            .distinct().toList();
        return tableInfoDAO.findAllById(ids).stream().collect(Collectors.toMap(TableInfoPO::getId, TableInfoPO::getName));
    }
}
