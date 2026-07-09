package com.poker.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_id", unique = true, length = 255)
    private String googleId;

    @Column(unique = true, nullable = false, updatable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(name = "avatar_filename", nullable = true)
    private String avatarFilename;

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

    public Account(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
        balance = 5000L;
    }
}
