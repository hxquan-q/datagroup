package com.dati.semantic.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricDimension {
    private String name;
    private String schema;
    private String tableName;
    private String column;
    private String description;
}
