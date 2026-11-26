package com.dati.semantic.server.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTableRelationRequest {
    @NotBlank @Size(max = 64)
    private String factTableId;
    @NotBlank @Size(max = 128)
    private String factColumn;
    @NotBlank @Size(max = 64)
    private String dimensionTableId;
    @NotBlank @Size(max = 128)
    private String dimensionColumn;
    @Size(max = 32)
    private String cardinality;
    @Size(max = 128)
    private String owner;
    @Size(max = 1024)
    private String description;
}
