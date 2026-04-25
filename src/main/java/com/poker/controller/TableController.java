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

    @Autowired
    public TableController(TableManager tableManager, AccountService accountService) {
        this.tableManager = tableManager;
        this.accountService = accountService;
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
    public TableDetailsDTO getTableDetails(@PathVariable String id) {
        Table table = tableManager.getTable(id);

        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        return TableDetailsDTO.createTableDetailsDTO(table);
    }

    @PostMapping("/{id}/join")
    public TableDetailsDTO joinTable(@RequestHeader("Authorization") String authHeader,
                                     @PathVariable String id,
                                     @RequestBody JoinRequestDTO request) {
        String token = extractToken(authHeader);

        accountService.validateSession(Long.parseLong(request.userId()), token);

        Table table = tableManager.getTable(id);

        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        if (tableManager.isPlayerActive(request.userId())) {
            throw new IllegalTableStateException("You are already playing at a table!");
        }

        int seatIndex = table.getFreeSeat();

        long userBuyIn = request.chips();

        if (userBuyIn < table.getMinBuyIn()) {
            throw new ChipAmountException("Insufficient buy-in. Minimum required: " + table.getMinBuyIn());
        }
        if (userBuyIn > table.getMaxBuyIn()) {
            throw new ChipAmountException("Buy-in exceeds limit. Maximum allowed: " + table.getMaxBuyIn());
        }

        Long userId = Long.parseLong(request.userId());
        accountService.withdrawFromWallet(userId, userBuyIn, id, TransactionType.BUY_IN);

        Account account = accountService.findById(userId);
        Player newPlayer = new Player(
                String.valueOf(account.getId()),
                account.getNickname(),
                seatIndex,
                new AtomicLong(account.getBalance()),
                new AtomicLong(userBuyIn)
        );

        table.joinTable(newPlayer);
        tableManager.registerPlayer(request.userId(), id);

        return TableDetailsDTO.createTableDetailsDTO(table);
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

        return TableDetailsDTO.createTableDetailsDTO(table);
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
            throw new ChipAmountException("Rebuy exceeds maximum table limit");
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

        Optional<Player> player = table.findPlayerById(request.userId());

        if  (player.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found");
        }

        PlayerAction action = new PlayerAction(request.type(), request.amount());
        table.handleAction(player.get(), action);

        return TableDetailsDTO.createTableDetailsDTO(table);
    }

    @PostMapping
    public IdResponseDTO createTable(@Valid @RequestBody CreateTableRequestDTO request) {
        String newId = tableManager.createTable(
                request.name(),
                request.smallBlind(),
                request.bigBlind(),
                request.minPlayersNum(),
                request.maxPlayersNum()
        );
        return new IdResponseDTO(newId);
    }

    @DeleteMapping("/{id}")
    public void deleteTable(@PathVariable String id) {
        Table removedTable = tableManager.removeTable(id);
        if (removedTable == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }
    }
}
