package com.poker.service;

import com.poker.dto.events.TableDetailsDTO;
import com.poker.exception.ChipAmountException;
import com.poker.exception.IllegalTableStateException;
import com.poker.model.*;
import com.poker.persistence.entity.Account;
import com.poker.persistence.entity.GameTable;
import com.poker.persistence.repository.GameTableRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Service
public class TableManager {
    private final Map<String, Table> tables = new ConcurrentHashMap<>();
    private final TableEventService tableEventService;
    private final Map<String, String> activePlayers = new ConcurrentHashMap<>();
    private final AccountService accountService;
    private final GameTableRepository tableRepository;
    private final GameEventPublisher eventPublisher;
    private final ScheduledExecutorService scheduler;

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder;

    private final Map<String, ScheduledFuture<?>> disconnectTasks = new ConcurrentHashMap<>();

    private static final int DISCONNECT_GRACE_PERIOD = 60;

    public TableManager(TableEventService tableEventService,
                        AccountService accountService,
                        GameTableRepository tableRepository,
                        GameEventPublisher eventPublisher,
                        ScheduledExecutorService scheduler,
                        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder) {
        this.tableEventService = tableEventService;
        this.accountService = accountService;
        this.tableRepository = tableRepository;
        this.eventPublisher = eventPublisher;
        this.scheduler = scheduler;
        this.passwordEncoder = passwordEncoder;
    }

    public void cashOutLeavingPlayer(String userId, long chips, String tableId) {
        try {
            accountService.depositToWallet(Long.parseLong(userId), chips, tableId, TransactionType.CASH_OUT);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to cash out {} chips for user {} from table {}", chips, userId, tableId, e);
            throw e;
        }
    }

    public boolean deleteCustomTable(String tableId) {
        try {
            UUID uuid = UUID.fromString(tableId);
            GameTable dbTable = tableRepository.findById(uuid).orElse(null);
            if (dbTable == null || dbTable.getIsSystem()) {
                return false;
            }
            tables.remove(tableId);
            tableRepository.delete(dbTable);
            log.info("Custom table [{}] was destroyed because it became empty.", dbTable.getName());
            return true;
        } catch (Exception e) {
            log.error("Error trying to delete empty custom table: {}", tableId, e);
            return false;
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
            cancelDisconnectTask(userId);

            ScheduledFuture<?> task = scheduler.schedule(() -> {
                if (isPlayerActive(userId)) {
                    forceKickPlayer(userId);
                    log.info("User {} was auto-kicked after {}s grace period.", userId, DISCONNECT_GRACE_PERIOD);
                }
                disconnectTasks.remove(userId);
            }, DISCONNECT_GRACE_PERIOD, TimeUnit.SECONDS);

            disconnectTasks.put(userId, task);
            log.info("Started {}s disconnect grace period timer for User {}", DISCONNECT_GRACE_PERIOD, userId);
        }
    }

    public void cancelDisconnectTask(String userId) {
        ScheduledFuture<?> task = disconnectTasks.remove(userId);
        if (task != null && !task.isDone()) {
            task.cancel(false);
            log.info("Cancelled disconnect grace period for User {}", userId);
        }
    }

    @Transactional
    public TableDetailsDTO createTable(String name, long smallBlind, long bigBlind, int minPlayersNum,
                                       int maxPlayersNum, String userId, long chips, String rawPasscode) {
        if (activePlayers.containsKey(userId)) {
            throw new IllegalTableStateException("error.player.already.playing");
        }

        String tableIdStr = UUID.randomUUID().toString();
        while (tables.containsKey(tableIdStr)) {
            tableIdStr = UUID.randomUUID().toString();
        }

        UUID tableUuid = UUID.fromString(tableIdStr);
        boolean isPrivate = rawPasscode != null && !rawPasscode.isBlank();

        String hashedPasscode = null;
        if (isPrivate) {
            hashedPasscode = passwordEncoder.encode(rawPasscode);
        }

        Long uId = Long.parseLong(userId);
        Account account = accountService.findById(uId);

        long minBuyIn = chips;

        long maxBuyIn = bigBlind * 100;

        if (minBuyIn > maxBuyIn) {
            throw new ChipAmountException("error.chips.max.buyin", maxBuyIn);
        }

        GameTable dbTable = new GameTable(
                tableUuid,
                name,
                smallBlind,
                bigBlind,
                minPlayersNum,
                maxPlayersNum,
                minBuyIn,
                maxBuyIn,
                isPrivate,
                hashedPasscode,
                false,
                account
        );
        tableRepository.save(dbTable);

        Table newTable = new Table(
                tableIdStr,
                name,
                smallBlind,
                bigBlind,
                minPlayersNum,
                maxPlayersNum,
                minBuyIn,
                isPrivate,
                hashedPasscode,
                tableEventService,
                this.scheduler
        );
        tables.put(tableIdStr, newTable);

        boolean withdrawn = false;
        try {
            accountService.withdrawFromWallet(uId, chips, tableIdStr, TransactionType.BUY_IN);
            withdrawn = true;
            Account refreshed = accountService.findById(uId);
            int seatIndex = newTable.getFreeSeat();

            Player creator = new Player(
                    userId,
                    refreshed.getNickname(),
                    refreshed.getAvatarFilename(),
                    seatIndex,
                    new java.util.concurrent.atomic.AtomicLong(refreshed.getBalance()),
                    new java.util.concurrent.atomic.AtomicLong(chips)
            );

            newTable.joinTable(creator);
            registerPlayer(userId, tableIdStr);

        } catch (Exception e) {
            tables.remove(tableIdStr);
            if (withdrawn) {
                try {
                    accountService.depositToWallet(uId, chips, tableIdStr, TransactionType.CASH_OUT);
                } catch (Exception refundError) {
                    log.error("CRITICAL: Failed to refund {} chips after createTable failure for user {}", chips, userId, refundError);
                }
            }
            throw e;
        }

        broadcastLobbyUpdate();

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
                    dbTable.getMinBuyIn(),
                    dbTable.getIsPrivate(),
                    dbTable.getPasscode(),
                    tableEventService,
                    this.scheduler
            );
            tables.put(memoryTable.getId(), memoryTable);
        }
    }

    @PreDestroy
    public void onServerShutdown() {
        log.warn("CRITICAL: Server is shutting down! Initiating emergency refunds for all active players...");

        for (Table table : tables.values()) {
            for (Player player : table.getPlayers()) {
                try {
                    long currentStack = player.getChips().get();

                    long moneyInPot = player.getTotalInHand();

                    long totalRefund = currentStack + moneyInPot;

                    if (totalRefund > 0) {
                        accountService.depositToWallet(
                                Long.parseLong(player.getUserId()),
                                totalRefund,
                                table.getId(),
                                TransactionType.SYSTEM_REFUND
                        );
                        log.info("Emergency refund: Returned {} chips to user {} (Stack: {}, In Pot: {})",
                                totalRefund, player.getUserId(), currentStack, moneyInPot);
                    }
                } catch (Exception e) {
                    log.error("Failed to refund chips to user {} at table {} during shutdown!",
                            player.getUserId(), table.getId(), e);
                }
            }
        }

        log.info("Emergency refunds completed successfully.");
    }

    public void broadcastLobbyUpdate() {
        List<com.poker.dto.TableDTO> currentLobby = getAllTables().stream()
                .map(com.poker.dto.TableDTO::createTableDTO)
                .toList();
        eventPublisher.publishFullLobbyUpdate(currentLobby);
    }
}