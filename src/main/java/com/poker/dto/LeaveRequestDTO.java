package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LeaveRequestDTO {
    @JsonProperty("user_id")
    private String userId;
}
