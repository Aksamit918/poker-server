package com.poker.service;

import com.poker.dto.events.PlayerStatusEvent;
import com.poker.dto.events.StreetEndDTO;
import com.poker.dto.events.TableDetailsDTO;
import com.poker.model.*;
import com.poker.persistence.entity.Account;
import com.poker.util.TableEventListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TableEventService implements TableEventListener {
    private final GameEventPublisher eventPublisher;
    private final AccountService accountService;
    private final TableManager tableManager;

    public TableEventService(GameEventPublisher eventPublisher,
                             AccountService accountService,
                             @Lazy TableManager tableManager) {
        this.eventPublisher = eventPublisher;
        this.accountService = accountService;
        this.tableManager = tableManager;
    }

    @Override
    public void onTableUpdate(Table table) {
        TableDetailsDTO dto = TableDetailsDTO.createTableDetailsDTO(table, null);
        eventPublisher.publishTableUpdate(dto);
    }

    @Override
    public void onHandFinished(List<String> playersInHand, Map<String, Long> winnersAndAmounts) {
        for (String userId : playersInHand) {
            boolean isWinner = winnersAndAmounts.containsKey(userId);
            long amountWon = isWinner ? winnersAndAmounts.get(userId) : 0;

            accountService.updatePlayerStats(userId, isWinner, amountWon);
        }
    }

    @Override
    public void onPlayerAction(String tableId, Player player, ActionType type, long amount, long pot) {
        Table table = tableManager.getTable(tableId);

        long timeToActMs = 0;
        int currentTurnSeat = -1;

        if (table != null) {
            currentTurnSeat = table.getActivePlayerIdx();
            if (currentTurnSeat != -1 && table.getState() != TableStates.SHOWDOWN) {
                long elapsed = System.currentTimeMillis() - table.getTurnStartTime();
                timeToActMs = Math.max(0, table.getTurnTimeoutMs() - elapsed);
            }
        }

        var actionEvent = new com.poker.dto.events.PlayerActionEvent(
                "PLAYER_ACTION",
                tableId,
                player.getSeatIndex(),
                type,
                amount,
                com.poker.dto.events.PlayerPublicStateDTO.fromPlayer(player),
                pot,
                currentTurnSeat,
                timeToActMs
        );

        if (table != null) {
            table.bufferEvent(actionEvent);
        }

        eventPublisher.publishPlayerAction(actionEvent);
    }

    @Override
    public void onPlayerJoin(String tableId, Player player) {
        var statusEvent = new PlayerStatusEvent(
                "PLAYER_STATUS",
                tableId,
                player.getSeatIndex(),
                "JOINED",
                player.getName()
        );

        Table table = tableManager.getTable(tableId);

        if (table != null) {
            table.bufferEvent(statusEvent);
            eventPublisher.publishPlayerStatus(statusEvent);
            eventPublisher.publishLobbyUpdate(tableId, table.getPlayerCount(), table.getMaxPlayers());
        } else {
            eventPublisher.publishPlayerStatus(statusEvent);
        }

        tableManager.broadcastLobbyUpdate();
    }

    @Override
    public void onPlayerLeave(String tableId, String userId, long chips, int seatIndex) {
        tableManager.cashOutLeavingPlayer(userId, chips, tableId);

        String realNickname = "Unknown";
        try {
            Account account = accountService.findById(Long.parseLong(userId));
            realNickname = account.getNickname();
        } catch (Exception ignored) {

        }

        var statusEvent = new PlayerStatusEvent(
                "PLAYER_STATUS",
                tableId,
                seatIndex,
                "LEFT",
                realNickname
        );

        Table table = tableManager.getTable(tableId);
        if (table != null) {
            table.bufferEvent(statusEvent);
        }

        eventPublisher.publishPlayerStatus(statusEvent);

        tableManager.unregisterPlayer(userId);

        if (table != null) {
            eventPublisher.publishLobbyUpdate(tableId, table.getPlayerCount(), table.getMaxPlayers());

            if (table.getPlayerCount() <= 1 && tableManager.deleteCustomTable(tableId)) {
                eventPublisher.publishLobbyUpdate(tableId, -1, table.getMaxPlayers());
            }

            tableManager.broadcastLobbyUpdate();
        }
    }

    @Override
    public void onStreetEnd(StreetEndDTO event) {
        Table table = tableManager.getTable(event.tableId());
        if (table != null) {
            table.bufferEvent(event);
        }
        eventPublisher.publishStreetEnd(event);
    }
}
