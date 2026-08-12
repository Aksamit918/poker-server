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
@Table(
        name = "user_emotes",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_emotes_user_emote", columnNames = {"user_id", "emote_id"})
)
public class UserEmote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "emote_id", nullable = false, length = 64)
    private String emoteId;

    @CreationTimestamp
    @Column(name = "purchased_at", nullable = false, updatable = false)
    private OffsetDateTime purchasedAt;

    public UserEmote(Long userId, String emoteId) {
        this.userId = userId;
        this.emoteId = emoteId;
    }
}
