package com.poker.controller;

import com.poker.dto.LoginResponseDTO;
import com.poker.model.Player;
import com.poker.persistence.entity.Account;
import com.poker.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.poker.model.Table;
import java.util.Optional;
import com.poker.service.TableManager;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final AccountService accountService;
    private final TableManager tableManager;

    public record RegisterRequest(String login, String password, String nickname) {}
    public record LoginRequest(String login, String password) {}
    public record ChangeNicknameRequest(String newNickname) {}
    public record ChangePasswordRequest(String oldPassword, String newPassword) {}
    public record LogoutRequest(String userId, String token) {}

    public AuthController(AccountService accountService, TableManager tableManager) {
        this.accountService = accountService;
        this.tableManager = tableManager;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            Account newAccount = accountService.register(
                    request.login(),
                    request.password(),
                    request.nickname()
            );
            return ResponseEntity.ok(newAccount);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LoginResponseDTO response = accountService.login(request.login(), request.password());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage()); // 401 Unauthorized
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody LogoutRequest request) {
        try {
            Long uId = Long.parseLong(request.userId());

            accountService.validateSession(uId, request.token());

            String tableId = tableManager.getTableIdByPlayer(request.userId());
            if (tableId != null) {
                Table table = tableManager.getTable(tableId);
                Optional<Player> player = table.findPlayerById(request.userId());
                player.ifPresent(table::leaveTable);
            }

            accountService.logout(uId);

            return ResponseEntity.ok("Logged out successfully");
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/nickname")
    public ResponseEntity<?> changeNickname(@PathVariable Long id, @RequestBody ChangeNicknameRequest request) {
        try {
            Account updated = accountService.changeNickname(id, request.newNickname());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<?> changePassword(@PathVariable Long id, @RequestBody ChangePasswordRequest request) {
        try {
            accountService.changePassword(id, request.oldPassword(), request.newPassword());
            return ResponseEntity.ok("Password updated successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable Long id) {
        try {
            accountService.deleteAccount(id);
            return ResponseEntity.ok("Account deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }
}
