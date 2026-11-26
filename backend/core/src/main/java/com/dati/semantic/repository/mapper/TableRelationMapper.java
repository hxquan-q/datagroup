package com.dati.semantic.repository.mapper;

import com.dati.base.MapperUtils;
import com.dati.semantic.domain.model.TableRelation;
import com.dati.semantic.repository.po.TableRelationPO;

public class TableRelationMapper {
    public static TableRelationPO toPO(TableRelation item) {
        TableRelationPO po = new TableRelationPO();
        MapperUtils.copyBaseResourceInfo(item, po);
        copyFields(item, po);
        return po;
    }
    public static void copyFields(TableRelation item, TableRelationPO po) {
        po.setSubjectId(item.getSubjectId());
        po.setFactTableId(item.getFactTableId());
        po.setFactColumn(item.getFactColumn());
        po.setDimensionTableId(item.getDimensionTableId());
        po.setDimensionColumn(item.getDimensionColumn());
        po.setCardinality(item.getCardinality());
        po.setOwner(item.getOwner());
        po.setRevision(item.getRevision());
    }
    public static TableRelation toModel(TableRelationPO po) {
        TableRelation item = new TableRelation();
        MapperUtils.copyBaseResourceInfo(po, item);
        item.setSubjectId(po.getSubjectId());
        item.setFactTableId(po.getFactTableId());
        item.setFactColumn(po.getFactColumn());
        item.setDimensionTableId(po.getDimensionTableId());
        item.setDimensionColumn(po.getDimensionColumn());
        item.setCardinality(po.getCardinality());
        item.setOwner(po.getOwner());
        item.setRevision(po.getRevision());
        return item;
    }
}
