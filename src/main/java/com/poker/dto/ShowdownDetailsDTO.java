package com.poker.dto;

import java.util.List;

public record ShowdownDetailsDTO(
        List<ShowdownPayoutDTO> payouts
) {}