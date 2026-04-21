package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class JoinRequestDTO {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("chips")
    private long chips;
}