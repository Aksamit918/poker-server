package com.poker.persistence.repository;

import com.poker.persistence.entity.UserEmote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserEmoteRepository extends JpaRepository<UserEmote, Long> {

    List<UserEmote> findByUserId(Long userId);

    boolean existsByUserIdAndEmoteId(Long userId, String emoteId);
}
