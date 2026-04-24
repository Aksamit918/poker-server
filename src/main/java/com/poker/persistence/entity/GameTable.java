package com.poker.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "game_tables")
@Data
@NoArgsConstructor
public class GameTable {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "small_blind", nullable = false)
    private Long smallBlind;

    @Column(name = "big_blind", nullable = false)
    private Long bigBlind;

    @Column(name = "min_players", nullable = false)
    private Integer minPlayers;

    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers;

    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate;

    @Column(name = "passcode", length = 50)
    private String passcode;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    private Account creator;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public GameTable(UUID id, String name, long smallBlind, long bigBlind, int minPlayers, int maxPlayers, boolean isPrivate, String passcode, boolean isSystem, Account creator) {
        this.id = id;
        this.name = name;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.isPrivate = isPrivate;
        this.passcode = passcode;
        this.isSystem = isSystem;
        this.creator = creator;
        this.createdAt = LocalDateTime.now();
    }
}