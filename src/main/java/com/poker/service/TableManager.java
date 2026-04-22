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
        String tableIdStr;

        do {
            tableIdStr = UUID.randomUUID().toString();
        } while (tables.containsKey(tableIdStr));

        UUID tableUuid = UUID.fromString(tableIdStr);

        GameTable dbTable = new GameTable(
                tableUuid,
                "Custom Table",
                smallBlind,
                bigBlind,
                minPlayersNum,
                maxPlayersNum,
                false,
                null,
                false,
                null
        );

        tableRepository.save(dbTable);

        PlayerLeaveListener listener = createLeaveListener(tableIdStr);
        Table newTable = new Table(tableIdStr, smallBlind, bigBlind, minPlayersNum, maxPlayersNum, listener);

        tables.put(tableIdStr, newTable);

        return tableIdStr;
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
            PlayerLeaveListener listener = createLeaveListener(dbTable.getId().toString());

            Table memoryTable = new Table(
                    dbTable.getId().toString(),
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

    private PlayerLeaveListener createLeaveListener(String tableId) {
        return (userId, chips) -> {
            accountService.depositToWallet(Long.parseLong(userId), chips, tableId, TransactionType.CASH_OUT);

            Table table = tables.get(tableId);
            if (table != null && table.getPlayers().isEmpty()) {
                tableRepository.findById(UUID.fromString(tableId)).ifPresent(dbTable -> {
                    if (!dbTable.isSystem()) {
                        tables.remove(tableId);
                        tableRepository.delete(dbTable);
                    }
                });
            }
        };
    }
}
