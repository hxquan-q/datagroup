package com.dati.semantic.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricParameter {
    private String name;
    private String type;
    private boolean required;
    private String defaultValue;
    private String description;
}
