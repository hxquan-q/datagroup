package com.dati.semantic.domain.model;

import com.dati.base.pojo.BaseResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class TableRelation extends BaseResource {
    private String subjectId;
    private String factTableId;
    private String factColumn;
    private String dimensionTableId;
    private String dimensionColumn;
    private String cardinality;
    private String owner;
    private Integer revision;
}
