package com.dati.semantic.server.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class TableRelationRecommendationVO {
    private String factTableId;
    private String factTableName;
    private String factColumn;
    private String dimensionTableId;
    private String dimensionTableName;
    private String dimensionColumn;
    private String cardinality;
    private String source;
    private Integer score;
    private String confidence;
    private List<String> evidence;
}
