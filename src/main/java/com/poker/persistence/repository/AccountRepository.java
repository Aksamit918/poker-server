package com.poker.persistence.repository;

import com.poker.persistence.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByGoogleId(String googleId);

    List<Account> findByNicknameContaining(String partOfName);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Account a SET a.balance = a.balance - :amount WHERE a.id = :id AND a.balance >= :amount")
    int withdrawBalance(@Param("id") Long id, @Param("amount") long amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Account a SET a.balance = a.balance + :amount WHERE a.id = :id")
    int depositBalance(@Param("id") Long id, @Param("amount") long amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Account a SET a.lastBonusAt = :at WHERE a.id = :id")
    int updateLastBonusAt(@Param("id") Long id, @Param("at") OffsetDateTime at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Account a SET
                a.handsPlayed = a.handsPlayed + 1,
                a.handsWon = a.handsWon + :wonInc,
                a.totalWon = a.totalWon + :amountWon,
                a.biggestPot = CASE WHEN :amountWon > a.biggestPot THEN :amountWon ELSE a.biggestPot END
            WHERE a.id = :id
            """)
    int updateHandStats(@Param("id") Long id, @Param("wonInc") int wonInc, @Param("amountWon") long amountWon);
}
