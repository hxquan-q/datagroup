package com.dati.semantic.server.pojo.vo;

import com.dati.base.pojo.BaseResourceVO;
import com.dati.semantic.domain.model.MetricParameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class MetricVO extends BaseResourceVO {
    private String subjectId;
    private String dataSourceId;
    private String code;
    private List<String> aliases = new ArrayList<>();
    private String owner;
    private String grain;
    private String timeField;
    private String timeZone;
    private List<String> allowedDimensions = new ArrayList<>();
    private String sqlTemplate;
    private List<MetricParameter> parameters = new ArrayList<>();
    private Integer timeout;
    private Integer maxRows;
    private Integer revision;
}
