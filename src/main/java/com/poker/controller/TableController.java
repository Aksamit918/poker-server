package com.poker.controller;

import com.poker.dto.*;
import com.poker.exception.ChipAmountException;
import com.poker.exception.IllegalTableStateException;
import com.poker.model.Player;
import com.poker.model.PlayerAction;
import com.poker.model.Table;
import com.poker.model.TableStates;
import com.poker.service.TableManager;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/tables")
public class TableController {
    private final TableManager tableManager;

    @Autowired
    public TableController(TableManager tableManager) {
        this.tableManager = tableManager;
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

        int minBuyIn = table.getMinBuyIn();
        int maxBuyIn = table.getMaxBuyIn();
        int userBuyIn = request.getChips();
        if (userBuyIn < minBuyIn) {
            throw new ChipAmountException("Insufficient buy-in. Minimum required: " + minBuyIn);
        } else if (userBuyIn > maxBuyIn) {
            throw new ChipAmountException("Buy-in exceeds limit. Maximum allowed: " + maxBuyIn);
        }

        if (request.getWalletBalance() < request.getChips()) {
            throw new ChipAmountException("Not enough money in wallet for this buy-in");
        }

        int remainingWallet = request.getWalletBalance() - request.getChips();

        Player newPlayer = new Player(
                request.getUserId(),
                request.getName(),
                new AtomicInteger(remainingWallet),
                new AtomicInteger(request.getChips())
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

    @PostMapping("/{id}/rebuy") //TODO finish this method
    public TableDetailsDTO rebuy(@PathVariable String id, @RequestBody RebuyRequestDTO request) {
        Table table = tableManager.getTable(id);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }

        Player player = table.findPlayerById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found at this table"));

        int fakeWalletBalance = player.getWalletBalance().get();

        table.rebuy(player, request.getAmount(), fakeWalletBalance);

        return TableDetailsDTO.createTableDetailsDTO(table);
    }
}
