package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefreshRequestDTO(@JsonProperty("refresh_token") String refreshToken) {}
