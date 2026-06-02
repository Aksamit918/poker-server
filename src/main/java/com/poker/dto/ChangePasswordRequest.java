package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @JsonProperty("old_password")
        @NotBlank(message = "Старый пароль не может быть пустым")
        String oldPassword,

        @JsonProperty("new_password")
        @NotBlank(message = "Новый пароль не может быть пустым")
        @Size(min = 6, message = "Пароль должен содержать минимум 6 символов")
        String newPassword
) {}