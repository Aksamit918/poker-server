package com.poker.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poker.dto.LoginResponseDTO;
import com.poker.exception.InvalidCredentialsException;
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
    public record LogoutRequest(
            @JsonProperty("user_id") String userId
    ) {}

    public AuthController(AccountService accountService, TableManager tableManager) {
        this.accountService = accountService;
        this.tableManager = tableManager;
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Missing or invalid Authorization header");
        }

        return authHeader.substring(7);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            LoginResponseDTO response = accountService.register(
                    request.login(),
                    request.password(),
                    request.nickname()
            );
            return ResponseEntity.ok(response);
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
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader,
                                    @RequestBody LogoutRequest request) {
        try {
            String token = extractToken(authHeader);
            Long uId = Long.parseLong(request.userId());

            accountService.validateSession(uId, token);

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
    public ResponseEntity<?> changeNickname(@RequestHeader("Authorization") String authHeader,
                                            @PathVariable Long id,
                                            @RequestBody ChangeNicknameRequest request) {
        try {
            String token = extractToken(authHeader);
            accountService.validateSession(id, token);

            Account updated = accountService.changeNickname(id, request.newNickname());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<?> changePassword(@RequestHeader("Authorization") String authHeader,
                                            @PathVariable Long id,
                                            @RequestBody ChangePasswordRequest request) {
        try {
            String token = extractToken(authHeader);
            accountService.validateSession(id, token);

            accountService.changePassword(id, request.oldPassword(), request.newPassword());
            return ResponseEntity.ok("Password updated successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@RequestHeader("Authorization") String authHeader,
                                           @PathVariable Long id) {
        try {
            String token = extractToken(authHeader);
            accountService.validateSession(id, token);

            accountService.deleteAccount(id);
            return ResponseEntity.ok("Account deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<?> getBalance(@RequestHeader("Authorization") String authHeader,
                                        @PathVariable Long id) {
        try {
            String token = extractToken(authHeader);
            accountService.validateSession(id, token);

            Account account = accountService.findById(id);

            return ResponseEntity.ok(new com.poker.dto.LeaveResponseDTO(account.getBalance()));

        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }
}
