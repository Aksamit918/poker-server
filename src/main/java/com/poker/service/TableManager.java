package com.poker.service;

import com.poker.dto.TableDetailsDTO;
import com.poker.exception.IllegalTableStateException;
import com.poker.model.*;
import com.poker.persistence.entity.Account;
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
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TableManager {
    private final Map<String, Table> tables = new ConcurrentHashMap<>();
    private final Map<String, String> activePlayers = new ConcurrentHashMap<>();
    private final AccountService accountService;
    private final GameTableRepository tableRepository;

    public TableManager(AccountService accountService, GameTableRepository tableRepository) {
        this.accountService = accountService;
        this.tableRepository = tableRepository;
    }

    public void forceKickPlayer(String userId) {
        String tableId = activePlayers.get(userId);

        if (tableId != null) {
            Table table = getTable(tableId);
            if (table != null) {
                table.findPlayerById(userId).ifPresent(table::leaveTable);
            }
        }
    }

    public TableDetailsDTO createTable(String name, long smallBlind, long bigBlind, int minPlayersNum, int maxPlayersNum, String userId, long chips) {

        if (activePlayers.containsKey(userId)) {
            throw new IllegalTableStateException("You are already playing at a table!");
        }

        String tableIdStr;
        do {
            tableIdStr = UUID.randomUUID().toString();
        } while (tables.containsKey(tableIdStr));

        UUID tableUuid = UUID.fromString(tableIdStr);

        GameTable dbTable = new GameTable(
                tableUuid,
                name,
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
        Table newTable = new Table(tableIdStr, name, smallBlind, bigBlind, minPlayersNum, maxPlayersNum, listener);

        tables.put(tableIdStr, newTable);

        try {
            Long uId = Long.parseLong(userId);
            accountService.withdrawFromWallet(uId, chips, tableIdStr, TransactionType.BUY_IN);

            Account account = accountService.findById(uId);

            int seatIndex = newTable.getFreeSeat();

            Player creator = new Player(
                    userId,
                    account.getNickname(),
                    seatIndex,
                    new AtomicLong(account.getBalance()),
                    new AtomicLong(chips)
            );

            newTable.joinTable(creator);

            activePlayers.put(userId, tableIdStr);

        } catch (Exception e) {
            tables.remove(tableIdStr);
            tableRepository.delete(dbTable);
            throw e;
        }

        return TableDetailsDTO.createTableDetailsDTO(newTable);
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
    public void registerPlayer(String userId, String tableId) {
        if (activePlayers.containsKey(userId)) {
            throw new IllegalTableStateException("You are already playing at another table!");
        }
        activePlayers.put(userId, tableId);
    }
    public void unregisterPlayer(String userId) {
        activePlayers.remove(userId);
    }
    public boolean isPlayerActive(String userId) {
        return activePlayers.containsKey(userId);
    }
    public String getTableIdByPlayer(String userId) {
        return activePlayers.get(userId);
    }

    @PostConstruct
    public void initSystemTables() {
        List<GameTable> systemTables = tableRepository.findByIsSystemTrue();

        for (GameTable dbTable : systemTables) {
            PlayerLeaveListener listener = createLeaveListener(dbTable.getId().toString());

            Table memoryTable = new Table(
                    dbTable.getId().toString(),
                    dbTable.getName(),
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

            unregisterPlayer(userId);

            Table table = tables.get(tableId);

            if (table != null && table.getPlayerCount() == 0) {

                tableRepository.findById(UUID.fromString(tableId)).ifPresent(dbTable -> {
                    if (!dbTable.getIsSystem()) {

                        tables.remove(tableId);
                        tableRepository.delete(dbTable);

                        System.out.println("DEBUG: Custom table [" + dbTable.getName() + "] closed and deleted.");
                    }
                });
            }
        };
    }
}
