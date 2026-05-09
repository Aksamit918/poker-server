package com.poker.service;

import com.poker.dto.TableDetailsDTO;
import com.poker.dto.events.PlayerStatusEvent; // Не забудь импорт
import com.poker.exception.IllegalTableStateException;
import com.poker.model.*;
import com.poker.persistence.entity.Account;
import com.poker.persistence.entity.GameTable;
import com.poker.persistence.repository.GameTableRepository;
import com.poker.util.TableEventListener;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TableManager implements TableEventListener {
    private final Map<String, Table> tables = new ConcurrentHashMap<>();
    private final Map<String, String> activePlayers = new ConcurrentHashMap<>();
    private final AccountService accountService;
    private final GameTableRepository tableRepository;
    private final GameEventPublisher eventPublisher;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, ScheduledFuture<?>> disconnectTasks = new ConcurrentHashMap<>();

    public TableManager(AccountService accountService,
                        GameTableRepository tableRepository,
                        GameEventPublisher eventPublisher) {
        this.accountService = accountService;
        this.tableRepository = tableRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onTableUpdate(Table table) {
        TableDetailsDTO dto = TableDetailsDTO.createTableDetailsDTO(table, null);
        eventPublisher.publishTableUpdate(dto);
    }

    @Override
    public void onPlayerLeave(String userId, long chips) {
        String tableId = activePlayers.get(userId);
        if (tableId == null) return;

        accountService.depositToWallet(Long.parseLong(userId), chips, tableId, TransactionType.CASH_OUT);

        String realNickname = "Unknown";
        try {
            Account account = accountService.findById(Long.parseLong(userId));
            realNickname = account.getNickname();
        } catch (Exception ignored) {
        }

        eventPublisher.publishPlayerStatus(new PlayerStatusEvent(
                "PLAYER_STATUS",
                tableId,
                -1,
                "LEFT",
                realNickname
        ));

        unregisterPlayer(userId);

        Table table = tables.get(tableId);
        if (table != null) {
            eventPublisher.publishLobbyUpdate(tableId, table.getPlayerCount(), table.getMaxPlayers());

            if (table.getPlayerCount() == 0) {
                tableRepository.findById(UUID.fromString(tableId)).ifPresent(dbTable -> {
                    if (!dbTable.getIsSystem()) {
                        tables.remove(tableId);
                        tableRepository.delete(dbTable);
                        System.out.println("DEBUG: Custom table [" + dbTable.getName() + "] deleted.");
                    }
                });
            }
        }
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

    public void scheduleDisconnectKick(String userId) {
        if (isPlayerActive(userId)) {
            ScheduledFuture<?> task = scheduler.schedule(() -> {
                if (isPlayerActive(userId)) {
                    forceKickPlayer(userId);
                }
                disconnectTasks.remove(userId);
            }, 3, TimeUnit.SECONDS);
            disconnectTasks.put(userId, task);
        }
    }

    public void cancelDisconnectTask(String userId) {
        ScheduledFuture<?> task = disconnectTasks.remove(userId);
        if (task != null) {
            task.cancel(false);
        }
    }

    public TableDetailsDTO createTable(String name, long smallBlind, long bigBlind, int minPlayersNum,
                                       int maxPlayersNum, String userId, long chips, String passcode) {
        if (activePlayers.containsKey(userId)) {
            throw new IllegalTableStateException("error.player.already.playing");
        }

        String tableIdStr = UUID.randomUUID().toString();
        while (tables.containsKey(tableIdStr)) {
            tableIdStr = UUID.randomUUID().toString();
        }

        UUID tableUuid = UUID.fromString(tableIdStr);
        boolean isPrivate = passcode != null && !passcode.isEmpty();

        GameTable dbTable = new GameTable(
                tableUuid, name, smallBlind, bigBlind, minPlayersNum, maxPlayersNum,
                isPrivate, passcode, false, null
        );
        tableRepository.save(dbTable);

        Table newTable = new Table(
                tableIdStr, name, smallBlind, bigBlind, minPlayersNum, maxPlayersNum,
                isPrivate, passcode, this
        );

        tables.put(tableIdStr, newTable);

        try {
            Long uId = Long.parseLong(userId);
            accountService.withdrawFromWallet(uId, chips, tableIdStr, TransactionType.BUY_IN);
            Account account = accountService.findById(uId);
            int seatIndex = newTable.getFreeSeat();

            Player creator = new Player(
                    userId, account.getNickname(), seatIndex,
                    new AtomicLong(account.getBalance()), new AtomicLong(chips)
            );

            newTable.joinTable(creator);
            registerPlayer(userId, tableIdStr);

        } catch (Exception e) {
            tables.remove(tableIdStr);
            tableRepository.delete(dbTable);
            throw e;
        }

        return TableDetailsDTO.createTableDetailsDTO(newTable, userId);
    }

    public Table getTable(String id) { return tables.get(id); }

    public Table removeTable(String id) { return tables.remove(id); }

    public List<Table> getAllTables() { return new ArrayList<>(tables.values()); }

    public void registerPlayer(String userId, String tableId) {
        activePlayers.put(userId, tableId);
        cancelDisconnectTask(userId);
    }

    public void unregisterPlayer(String userId) {
        activePlayers.remove(userId);
        cancelDisconnectTask(userId);
    }

    public boolean isPlayerActive(String userId) { return activePlayers.containsKey(userId); }

    public String getTableIdByPlayer(String userId) { return activePlayers.get(userId); }

    @PostConstruct
    public void initSystemTables() {
        List<GameTable> systemTables = tableRepository.findByIsSystemTrue();
        for (GameTable dbTable : systemTables) {
            Table memoryTable = new Table(
                    dbTable.getId().toString(),
                    dbTable.getName(),
                    dbTable.getSmallBlind(),
                    dbTable.getBigBlind(),
                    dbTable.getMinPlayers(),
                    dbTable.getMaxPlayers(),
                    dbTable.getIsPrivate(),
                    dbTable.getPasscode(),
                    this
            );
            tables.put(memoryTable.getId(), memoryTable);
        }
    }
}