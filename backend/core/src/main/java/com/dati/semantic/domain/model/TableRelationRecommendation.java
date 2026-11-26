package com.dati.semantic.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Candidate table relation inferred from governed metadata and dictionary evidence. */
@Data
public class TableRelationRecommendation {
    private String factTableId;
    private String factTableName;
    private String factColumn;
    private String dimensionTableId;
    private String dimensionTableName;
    private String dimensionColumn;
    private String cardinality;
    private Integer score;
    private String confidence;
    private List<String> evidence = new ArrayList<>();
}
