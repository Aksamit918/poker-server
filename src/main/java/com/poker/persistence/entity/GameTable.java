package com.poker.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_tables")
@Data
@NoArgsConstructor
public class GameTable {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "small_blind", nullable = false)
    private int smallBlind;

    @Column(name = "big_blind", nullable = false)
    private int bigBlind;

    @Column(name = "min_players", nullable = false)
    private int minPlayers;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    private String passcode;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    private Account creator;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}