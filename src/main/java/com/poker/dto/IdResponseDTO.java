package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IdResponseDTO {
    @JsonProperty("table_id")
    private String id;
}
