package com.dati.semantic.server.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateValidationCaseRequest {
    @NotBlank @Size(max = 64)
    private String name;
    @Size(max = 1024)
    private String description;
    @NotBlank
    private String metricId;
    @NotBlank @Size(max = 1024)
    private String question;
    private Map<String, Object> arguments;
    private List<String> dimensions;
    @Size(max = 64)
    private String assertionType;
    @Size(max = 1024)
    private String expectedValue;
    @Size(max = 128)
    private String owner;
    private Boolean enabled = true;
}
