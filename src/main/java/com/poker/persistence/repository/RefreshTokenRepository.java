package com.poker.persistence.repository;

import com.poker.persistence.entity.Account;
import com.poker.persistence.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByAccount(Account account);
}