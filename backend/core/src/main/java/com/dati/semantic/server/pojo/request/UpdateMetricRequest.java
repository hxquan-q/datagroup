package com.dati.semantic.server.pojo.request;

import com.dati.semantic.domain.model.MetricDimension;
import com.dati.semantic.domain.model.MetricParameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateMetricRequest {
    @Size(max = 64)
    private String name;

    @Size(max = 1024)
    private String description;

    private List<String> aliases;

    @Size(max = 128)
    private String owner;

    @Size(max = 128)
    private String grain;

    @Size(max = 128)
    private String timeField;

    @Size(max = 64)
    private String timeZone;

    private List<String> allowedDimensions;

    private List<MetricDimension> dimensionMappings;

    private String measureExpression;

    @Size(max = 128)
    private String factSchema;

    @Size(max = 128)
    private String factTableName;

    private String sqlTemplate;

    private List<MetricParameter> parameters;

    @Min(1)
    @Max(300)
    private Integer timeout;

    @Min(1)
    @Max(10000)
    private Integer maxRows;
}
