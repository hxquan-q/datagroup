package com.dati.semantic.server.assembler;

import com.dati.base.BaseAssembler;
import com.dati.base.pojo.PageResponse;
import com.dati.semantic.domain.model.TableRelation;
import com.dati.semantic.server.pojo.vo.TableRelationVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TableRelationAssembler extends BaseAssembler {
    public TableRelationVO toVO(TableRelation item, Map<String, String> tableNames) {
        TableRelationVO vo = mapFields(item, tableNames);
        super.fillUserInfo(List.of(vo));
        return vo;
    }
    public PageResponse<TableRelationVO> toPageResponse(Page<TableRelation> page, Map<String, String> tableNames) {
        List<TableRelationVO> vos = page.getContent().stream().map(item -> mapFields(item, tableNames)).collect(Collectors.toList());
        super.fillUserInfo(vos);
        return PageResponse.of(new PageImpl<>(vos, page.getPageable(), page.getTotalElements()));
    }
    private TableRelationVO mapFields(TableRelation item, Map<String, String> tableNames) {
        TableRelationVO vo = new TableRelationVO();
        super.copyBaseInfo(item, vo);
        vo.setSubjectId(item.getSubjectId());
        vo.setFactTableId(item.getFactTableId());
        vo.setFactTableName(tableNames.get(item.getFactTableId()));
        vo.setFactColumn(item.getFactColumn());
        vo.setDimensionTableId(item.getDimensionTableId());
        vo.setDimensionTableName(tableNames.get(item.getDimensionTableId()));
        vo.setDimensionColumn(item.getDimensionColumn());
        vo.setCardinality(item.getCardinality());
        vo.setOwner(item.getOwner());
        vo.setRevision(item.getRevision());
        return vo;
    }
}
