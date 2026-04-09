package com.poker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.model.Player;
import com.poker.model.PlayerStatus; // Убедись, что импортировал статус
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlayerDTO {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("chips")
    private int chips;

    @JsonProperty("status")
    private String status;

    @JsonProperty("is_active")
    private boolean isActive;

    public static PlayerDTO fromPlayer(Player player) {
        return new PlayerDTO(
                player.getUserId(),
                player.getName(),
                player.getChips().get(),
                player.getStatus().name(),
                player.getStatus() != PlayerStatus.FOLDED
        );
    }
}