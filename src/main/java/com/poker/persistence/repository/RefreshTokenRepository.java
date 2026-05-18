package com.poker.persistence.repository;

import com.poker.persistence.entity.Account;
import com.poker.persistence.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByAccount(Account account);
    Optional<RefreshToken> findByAccount(Account account);
    @Modifying
    @Transactional
    void deleteByExpiryDateBefore(Instant now);
}