package com.poker.service;

import com.poker.model.*;
import com.poker.persistence.entity.GameTable;
import com.poker.persistence.repository.GameTableRepository;
import com.poker.util.PlayerLeaveListener;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TableManager {
    private final Map<String, Table> tables = new ConcurrentHashMap<>();
    private final AccountService accountService;
    private final GameTableRepository tableRepository;
    public TableManager(AccountService accountService, GameTableRepository tableRepository) {
        this.accountService = accountService;
        this.tableRepository = tableRepository;
    }
    public String createTable(int smallBlind, int bigBlind, int minPlayersNum, int maxPlayersNum) {
        String id = null;

        do {
            id = UUID.randomUUID().toString();
        } while (tables.containsKey(id));

        PlayerLeaveListener listener = (userId, chips) -> {
            if (chips > 0) {
                accountService.depositToWallet(Long.parseLong(userId), chips);
            }
        };

        Table newTable = new Table(id, smallBlind, bigBlind, minPlayersNum, maxPlayersNum, listener);
        tables.put(id, newTable);

        return id;
    }
    public Table getTable(String id) {
        return tables.get(id);
    }
    public Table removeTable(String id) {
        return tables.remove(id);
    }
    public List<Table> getAllTables() {
        return new ArrayList<>(tables.values());
    }
    @PostConstruct
    public void initSystemTables() {
        List<GameTable> systemTables = tableRepository.findByIsSystemTrue();

        for (GameTable dbTable : systemTables) {
            PlayerLeaveListener listener = (userId, chips) -> {
                if (chips > 0) {
                    accountService.depositToWallet(Long.parseLong(userId), chips);
                }
            };

            Table memoryTable = new Table(
                    dbTable.getId(),
                    dbTable.getSmallBlind(),
                    dbTable.getBigBlind(),
                    dbTable.getMinPlayers(),
                    dbTable.getMaxPlayers(),
                    listener
            );

            tables.put(memoryTable.getId(), memoryTable);
        }
        System.out.println("Loaded " + systemTables.size() + " system tables from DB.");
    }
}
