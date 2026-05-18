package com.poker.controller;

import com.poker.dto.*;
import com.poker.exception.ChipAmountException;
import com.poker.exception.IllegalTableStateException;
import com.poker.model.Player;
import com.poker.model.PlayerAction;
import com.poker.model.Table;
import com.poker.model.TransactionType;
import com.poker.persistence.entity.Account;
import com.poker.service.AccountService;
import com.poker.service.TableManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/tables")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TableController {

    private final TableManager tableManager;
    private final AccountService accountService;

    private String getAuthenticatedUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    public List<TableDTO> getLobby() {
        Collection<Table> tables = tableManager.getAllTables();
        return tables.stream()
                .map(TableDTO::createTableDTO)
                .toList();
    }

    @GetMapping("/{id}")
    public TableDetailsDTO getTableDetails(@PathVariable String id) {
        Table table = tableManager.getTable(id);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        String requestingUserId = getAuthenticatedUserId();
        return TableDetailsDTO.createTableDetailsDTO(table, requestingUserId);
    }

    @PostMapping("/{id}/join")
    public TableDetailsDTO joinTable(@PathVariable String id, @RequestBody JoinRequestDTO request) {
        String authUserId = getAuthenticatedUserId();
        Long userId = Long.parseLong(authUserId);

        Table table = tableManager.getTable(id);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.table.not.found");
        }

        String activeTableId = tableManager.getTableIdByPlayer(authUserId);
        if (activeTableId != null) {
            if (activeTableId.equals(id)) {
                tableManager.cancelDisconnectTask(authUserId);
                return TableDetailsDTO.createTableDetailsDTO(table, authUserId);
            } else {
                throw new IllegalTableStateException("error.player.already.playing");
            }
        }

        if (table.isPrivate()) {
            if (request.passcode() == null || request.passcode().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.passcode.required");
            }
            if (!table.getPasscode().equals(request.passcode())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.passcode.wrong");
            }
        }

        long userBuyIn = request.chips();
        if (userBuyIn < table.getMinBuyIn() || userBuyIn > table.getMaxBuyIn()) {
            throw new ChipAmountException("error.chips.amount.invalid");
        }

        accountService.withdrawFromWallet(userId, userBuyIn, id, TransactionType.BUY_IN);
        Account account = accountService.findById(userId);

        Player newPlayer = new Player(
                authUserId,
                account.getNickname(),
                table.getFreeSeat(),
                new AtomicLong(account.getBalance()),
                new AtomicLong(userBuyIn)
        );

        table.joinTable(newPlayer);
        tableManager.registerPlayer(authUserId, id);

        return TableDetailsDTO.createTableDetailsDTO(table, authUserId);
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Map<String, String>> leaveTable(@PathVariable String id) {
        String authUserId = getAuthenticatedUserId();

        Table table = tableManager.getTable(id);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.table.not.found");
        }

        Player player = table.findPlayerById(authUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        table.leaveTable(player);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Player left the table"));
    }

    @PostMapping("/{id}/rebuy")
    public RebuyResponseDTO rebuy(@PathVariable String id, @RequestBody RebuyRequestDTO request) {
        String authUserId = getAuthenticatedUserId();

        Table table = tableManager.getTable(id);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        Player player = table.findPlayerById(authUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        long amount = request.amount();

        if (player.getChips().get() + amount > table.getMaxBuyIn()) {
            throw new ChipAmountException("Rebuy amount exceeds the maximum table limit: " + table.getMaxBuyIn());
        }
        if (player.getChips().get() + amount < table.getBigBlindBet()) {
            throw new ChipAmountException("Total stack after rebuy must meet the minimum requirement of " + table.getBigBlindBet());
        }

        accountService.withdrawFromWallet(Long.parseLong(authUserId), amount, id, TransactionType.REBUY);

        Account account = accountService.findById(Long.parseLong(authUserId));
        long realWalletBalance = account.getBalance();

        table.rebuy(player, amount, realWalletBalance);

        return new RebuyResponseDTO(player.getChips().get());
    }

    @PostMapping("/{id}/action")
    public ResponseEntity<?> action(@PathVariable String id, @RequestBody ActionRequestDTO request) {
        String authUserId = getAuthenticatedUserId();
        System.out.println("\n[DEBUG] === ЗАПРОС НА ДЕЙСТВИЕ ===");
        System.out.println("[DEBUG] User: " + authUserId + ", Action: " + request.type() + ", Amount: " + request.amount());

        Table table = tableManager.getTable(id);
        if (table == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Table not found"));

        Optional<Player> playerOpt = table.findPlayerById(authUserId);
        if (playerOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Player not found"));

        Player player = playerOpt.get();
        PlayerAction action = new PlayerAction(request.type(), request.amount());

        System.out.println("[DEBUG] Пытаемся выполнить handleAction...");
        table.handleAction(player, action);

        System.out.println("[DEBUG] Действие успешно выполнено (этого лога не должно быть при ошибке)");
        return ResponseEntity.ok(TableDetailsDTO.createTableDetailsDTO(table, authUserId));
    }

    @PostMapping
    public TableDetailsDTO createTable(@Valid @RequestBody CreateTableRequestDTO request) {
        String authUserId = getAuthenticatedUserId();

        return tableManager.createTable(
                request.name(),
                request.smallBlind(),
                request.bigBlind(),
                request.minPlayersNum(),
                request.maxPlayersNum(),
                authUserId, 
                request.chips(),
                request.passcode()
        );
    }

    @DeleteMapping("/{id}")
    public void deleteTable(@PathVariable String id) {
        Table removedTable = tableManager.removeTable(id);
        if (removedTable == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }
    }
}