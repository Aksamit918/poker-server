package com.poker.dto;

public record ChangePasswordRequest(String oldPassword, String newPassword) {}
