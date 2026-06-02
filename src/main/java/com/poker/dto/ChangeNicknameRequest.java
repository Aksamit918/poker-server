package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChangeNicknameRequest(
        @JsonProperty("new_nickname") String newNickname
) {}
