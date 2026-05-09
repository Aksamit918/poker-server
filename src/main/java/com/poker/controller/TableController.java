package com.poker.controller;

import com.poker.dto.*;
import com.poker.exception.ChipAmountException;
import com.poker.exception.IllegalTableStateException;
import com.poker.exception.InvalidCredentialsException;
import com.poker.model.Player;
import com.poker.model.PlayerAction;
import com.poker.model.Table;
import com.poker.model.TransactionType;
import com.poker.persistence.entity.Account;
import com.poker.service.AccountService;
import com.poker.service.GameEventPublisher;
import com.poker.service.TableManager;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/tables")
public class TableController {
    private final TableManager tableManager;
    private final AccountService accountService;
    private final GameEventPublisher eventPublisher;

    @Autowired
    public TableController(TableManager tableManager, AccountService accountService, GameEventPublisher eventPublisher) {
        this.tableManager = tableManager;
        this.accountService = accountService;
        this.eventPublisher = eventPublisher;
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Missing or invalid Authorization header");
        }

        return authHeader.substring(7);
    }

    @GetMapping
    public List<TableDTO> getLobby() {
        Collection<Table> tables = tableManager.getAllTables();

        return tables.stream()
                .map(TableDTO::createTableDTO)
                .toList();
    }

    @GetMapping("/{id}")
    public TableDetailsDTO getTableDetails(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String id) {

        Table table = tableManager.getTable(id);

        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        String requestingUserId = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            requestingUserId = accountService.getUserIdByToken(token);
        }

        return TableDetailsDTO.createTableDetailsDTO(table, requestingUserId);
    }

    @PostMapping("/{id}/join")
    public TableDetailsDTO joinTable(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id,
            @RequestBody JoinRequestDTO request) {

        String token = extractToken(authHeader);
        accountService.validateSession(Long.parseLong(request.userId()), token);

        Table table = tableManager.getTable(id);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.table.not.found");
        }

        if (tableManager.isPlayerActive(request.userId())) {
            throw new IllegalTableStateException("error.player.already.playing");
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

        Long userId = Long.parseLong(request.userId());
        accountService.withdrawFromWallet(userId, userBuyIn, id, TransactionType.BUY_IN);

        Account account = accountService.findById(userId);

        Player newPlayer = new Player(
                String.valueOf(account.getId()),
                account.getNickname(),
                table.getFreeSeat(),
                new AtomicLong(account.getBalance()),
                new AtomicLong(userBuyIn)
        );

        table.joinTable(newPlayer);
        tableManager.registerPlayer(request.userId(), id);

        return TableDetailsDTO.createTableDetailsDTO(table, request.userId());
    }


    @PostMapping("/{id}/leave")
    public TableDetailsDTO leaveTable(@RequestHeader("Authorization") String authHeader,
                                      @PathVariable String id,
                                      @RequestBody LeaveRequestDTO request) {
        String token = extractToken(authHeader);

        accountService.validateSession(Long.parseLong(request.userId()), token);

        Table table = tableManager.getTable(id);

        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        Player player = table.findPlayerById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        table.leaveTable(player);

        return TableDetailsDTO.createTableDetailsDTO(table, request.userId());
    }

    @PostMapping("/{id}/rebuy")
    public RebuyResponseDTO rebuy(@RequestHeader("Authorization") String authHeader,
                                  @PathVariable String id,
                                  @RequestBody RebuyRequestDTO request) {
        String token = extractToken(authHeader);
        accountService.validateSession(Long.parseLong(request.userId()), token);

        Table table = tableManager.getTable(id);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        Player player = table.findPlayerById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        long amount = request.amount();

        if (player.getChips().get() + amount > table.getMaxBuyIn()) {
            throw new ChipAmountException("Rebuy amount exceeds the maximum table limit: " + table.getMaxBuyIn());
        }

        if (player.getChips().get() + amount < table.getBigBlindBet()) {
            throw new ChipAmountException("Total stack after rebuy must meet the minimum requirement of " + table.getBigBlindBet());
        }

        accountService.withdrawFromWallet(Long.parseLong(player.getUserId()), amount, id, TransactionType.REBUY);

        Account account = accountService.findById(Long.parseLong(player.getUserId()));
        long realWalletBalance = account.getBalance();

        table.rebuy(player, amount, realWalletBalance);

        return new RebuyResponseDTO(
                player.getChips().get(),
                realWalletBalance
        );
    }

    @PostMapping("/{id}/action")
    public TableDetailsDTO action(@RequestHeader("Authorization") String authHeader,
                                  @PathVariable String id,
                                  @RequestBody ActionRequestDTO request) {
        String token = extractToken(authHeader);
        accountService.validateSession(Long.parseLong(request.userId()), token);

        Table table = tableManager.getTable(id);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        Optional<Player> playerOpt = table.findPlayerById(request.userId());
        if (playerOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found");
        }

        Player player = playerOpt.get();
        PlayerAction action = new PlayerAction(request.type(), request.amount());

        table.handleAction(player, action);

        return TableDetailsDTO.createTableDetailsDTO(table, request.userId());
    }

    @PostMapping
    public TableDetailsDTO createTable(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateTableRequestDTO request) {

        String token = extractToken(authHeader);
        accountService.validateSession(Long.parseLong(request.userId()), token);

        TableDetailsDTO dto = tableManager.createTable(
                request.name(),
                request.smallBlind(),
                request.bigBlind(),
                request.minPlayersNum(),
                request.maxPlayersNum(),
                request.userId(),
                request.chips(),
                request.passcode()
        );

        return dto;
    }

    @DeleteMapping("/{id}")
    public void deleteTable(@PathVariable String id) {
        Table removedTable = tableManager.removeTable(id);
        if (removedTable == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }
    }
}
