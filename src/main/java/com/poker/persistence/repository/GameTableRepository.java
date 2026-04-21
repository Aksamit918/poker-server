package com.poker.persistence.repository;

import com.poker.persistence.entity.GameTable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GameTableRepository extends JpaRepository<GameTable, String> {
    List<GameTable> findByIsSystemTrue();
}