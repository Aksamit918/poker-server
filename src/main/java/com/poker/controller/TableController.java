package com.poker.controller;

import com.poker.dto.*;
import com.poker.exception.ChipAmountException;
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
    public TableDetailsDTO joinTable(@PathVariable String id, @RequestBody JoinRequestDTO request) {
        Table table = tableManager.getTable(id);

        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        long userBuyIn = request.getChips();

        if (userBuyIn < table.getMinBuyIn()) {
            throw new ChipAmountException("Insufficient buy-in. Minimum required: " + table.getMinBuyIn());
        }
        if (userBuyIn > table.getMaxBuyIn()) {
            throw new ChipAmountException("Buy-in exceeds limit. Maximum allowed: " + table.getMaxBuyIn());
        }

        Long userId = Long.parseLong(request.getUserId());
        accountService.withdrawFromWallet(userId, userBuyIn, id, TransactionType.BUY_IN);

        Account account = accountService.findById(userId);
        Player newPlayer = new Player(
                String.valueOf(account.getId()),
                account.getNickname(),
                new AtomicLong(account.getBalance()),
                new AtomicLong(userBuyIn)
        );

        table.joinTable(newPlayer);

        return TableDetailsDTO.createTableDetailsDTO(table);
    }

    @PostMapping("/{id}/leave")
    public TableDetailsDTO leaveTable(@PathVariable String id, @RequestBody LeaveRequestDTO request) {
        Table table = tableManager.getTable(id);

        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        Player player = table.findPlayerById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        table.leaveTable(player);

        return TableDetailsDTO.createTableDetailsDTO(table);
    }

    @PostMapping("/{id}/rebuy")
    public TableDetailsDTO rebuy(@PathVariable String id, @RequestBody RebuyRequestDTO request) {
        Table table = tableManager.getTable(id);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        Player player = table.findPlayerById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found at this table"));

        long amount = request.getAmount();

        long currentChips = player.getChips().get();
        if (currentChips + amount > table.getMaxBuyIn()) {
            throw new ChipAmountException("Rebuy exceeds maximum table limit");
        }

        accountService.withdrawFromWallet(Long.parseLong(player.getUserId()), amount, id, TransactionType.REBUY);

        Account account = accountService.findById(Long.parseLong(player.getUserId()));
        long realWalletBalance = account.getBalance();

        table.rebuy(player, amount, realWalletBalance);

        return TableDetailsDTO.createTableDetailsDTO(table);
    }

    @PostMapping("/{id}/action")
    public TableDetailsDTO action(@PathVariable String id, @RequestBody ActionRequestDTO request) {
        Table table = tableManager.getTable(id);

        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        Optional<Player> player = table.findPlayerById(request.getUserId());

        if  (player.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found");
        }

        PlayerAction action = new PlayerAction(request.getType(), request.getAmount());
        table.handleAction(player.get(), action);

        return TableDetailsDTO.createTableDetailsDTO(table);
    }

    @PostMapping
    public IdResponseDTO createTable(@Valid @RequestBody CreateTableRequestDTO request) {
        String newId = tableManager.createTable(request.getSmallBlind(), request.getBigBlind(),
                request.getMinPlayersNum(), request.getMaxPlayersNum());
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
