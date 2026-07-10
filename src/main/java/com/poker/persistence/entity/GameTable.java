package com.poker.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "game_tables")
@Check(constraints = "small_blind > 0 AND big_blind > 0 AND small_blind < big_blind")
@Check(constraints = "min_players >= 2 AND max_players <= 10 AND min_players <= max_players")
public class GameTable {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "small_blind", nullable = false)
    private Long smallBlind;

    @Column(name = "big_blind", nullable = false)
    private Long bigBlind;

    @Column(name = "min_players", nullable = false)
    private Integer minPlayers;

    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers;

    @Column(name = "min_buy_in", nullable = false)
    private Long minBuyIn;

    @Column(name = "max_buy_in", nullable = false)
    private Long maxBuyIn;

    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate;

    @Column(name = "passcode", length = 60)
    private String passcode;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    private Account creator;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    public GameTable(UUID id, String name, long smallBlind, long bigBlind, int minPlayers, int maxPlayers,
                     long minBuyIn, long maxBuyIn, boolean isPrivate, String passcode, boolean isSystem, Account creator) {
        this.id = id;
        this.name = name;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.minBuyIn = minBuyIn;
        this.maxBuyIn = maxBuyIn;
        this.isPrivate = isPrivate;
        this.passcode = passcode;
        this.isSystem = isSystem;
        this.creator = creator;
    }
}