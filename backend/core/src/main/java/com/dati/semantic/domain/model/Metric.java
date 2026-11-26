package com.dati.semantic.domain.model;

import com.dati.base.pojo.BaseResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class Metric extends BaseResource {

    private String subjectId;
    private String dataSourceId;
    private String code;
    private List<String> aliases = new ArrayList<>();
    private String owner;
    private String grain;
    private String timeField;
    private String timeZone;
    private List<String> allowedDimensions = new ArrayList<>();
    private List<MetricDimension> dimensionMappings = new ArrayList<>();
    private String measureExpression;
    private String factSchema;
    private String factTableName;
    private String sqlTemplate;
    private List<MetricParameter> parameters = new ArrayList<>();
    private Integer timeout = 30;
    private Integer maxRows = 1000;
    private Integer revision = 1;
}
