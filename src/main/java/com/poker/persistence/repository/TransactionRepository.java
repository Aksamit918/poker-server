package com.poker.persistence.repository;

import com.poker.persistence.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccount_IdOrderByCreatedAtDesc(Long accountId);

    List<Transaction> findByGameTable_Id(UUID tabledId);
}
