package com.poker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 1, max = 20)
        String login,

        @NotBlank

        String password,

        @NotBlank
        @Size(min = 1, max = 20)
        String nickname
) {}
