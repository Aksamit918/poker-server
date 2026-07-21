package com.poker.dto;

public record ErrorResponseDTO(
        String errorType,
        String message
) {}
