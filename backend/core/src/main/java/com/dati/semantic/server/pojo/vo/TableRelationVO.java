package com.dati.semantic.server.pojo.vo;

import com.dati.base.pojo.BaseResourceVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class TableRelationVO extends BaseResourceVO {
    private String subjectId;
    private String factTableId;
    private String factTableName;
    private String factColumn;
    private String dimensionTableId;
    private String dimensionTableName;
    private String dimensionColumn;
    private String cardinality;
    private String owner;
    private Integer revision;
}
