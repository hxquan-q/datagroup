package com.dati.semantic.server.pojo.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTableRelationRequest {
    @Size(max = 128)
    private String factColumn;
    @Size(max = 128)
    private String dimensionColumn;
    @Size(max = 32)
    private String cardinality;
    @Size(max = 128)
    private String owner;
    @Size(max = 1024)
    private String description;
}
