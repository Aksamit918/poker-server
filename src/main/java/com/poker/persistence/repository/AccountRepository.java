package com.poker.persistence.repository;

import com.poker.persistence.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByGoogleId(String googleId);

    List<Account> findByNicknameContaining(String partOfName);
}
