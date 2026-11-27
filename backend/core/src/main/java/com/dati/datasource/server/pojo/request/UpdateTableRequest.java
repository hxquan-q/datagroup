package com.dati.datasource.server.pojo.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UpdateTableRequest {

    @Size(max = 1024)
    private String description;

    private List<String> aliases = new ArrayList<>();

    @Size(max = 128)
    private String timeColumn;

    private Boolean timeWindowRequired;

    private Integer maxRangeDays;

}
