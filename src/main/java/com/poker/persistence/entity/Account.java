package com.poker.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false, length = 20)
    private String login;

    @Column(name= "password_hash", nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(name = "wallet_balance", nullable = false)
    private Long balance;

    @Column(name = "last_bonus_at", nullable = true)
    private OffsetDateTime lastBonusAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "hands_played", nullable = false)
    private Integer handsPlayed = 0;

    @Column(name = "hands_won", nullable = false)
    private Integer handsWon = 0;

    @Column(name = "total_won", nullable = false)
    private Long totalWon = 0L;

    @Column(name = "biggest_pot", nullable = false)
    private Long biggestPot = 0L;

    public Account(String login, String password, String nickname) {
        this.login =  login;
        this.password = password;
        this.nickname = nickname;
        balance = 5000L;
    }
}
