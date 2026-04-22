package com.poker.persistence.repository;

import com.poker.persistence.entity.GameTable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface GameTableRepository extends JpaRepository<GameTable, UUID> {
    List<GameTable> findByIsSystemTrue();
}