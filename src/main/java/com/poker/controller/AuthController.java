package com.poker.controller;

import com.poker.dto.LoginResponseDTO;
import com.poker.exception.InvalidCredentialsException;
import com.poker.model.Player;
import com.poker.model.Table;
import com.poker.persistence.entity.Account;
import com.poker.service.AccountService;
import com.poker.service.JwtService;
import com.poker.service.TableManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {
    private final AccountService accountService;
    private final TableManager tableManager;
    private final JwtService jwtService;

    public record RegisterRequest(String login, String password, String nickname) {}
    public record LoginRequest(String login, String password) {}
    public record ChangeNicknameRequest(String newNickname) {}
    public record ChangePasswordRequest(String oldPassword, String newPassword) {}
    public record RefreshRequestDTO(String refreshToken) {}
    public record RefreshResponseDTO(String accessToken) {}

    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("error.token.missing");
        }
        return authHeader.substring(7);
    }

    @PostMapping("/register")
    public LoginResponseDTO register(@RequestBody RegisterRequest request) {
        return accountService.register(request.login(), request.password(), request.nickname());
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody LoginRequest request) {
        return accountService.login(request.login(), request.password());
    }

    @PostMapping("/refresh")
    public RefreshResponseDTO refreshToken(@RequestBody RefreshRequestDTO request) {
        String newAccessToken = accountService.refreshAccessToken(request.refreshToken());
        return new RefreshResponseDTO(newAccessToken);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        String userIdStr = jwtService.extractUserId(token);

        if (userIdStr == null) throw new InvalidCredentialsException("error.session.expired");

        Long uId = Long.parseLong(userIdStr);

        String tableId = tableManager.getTableIdByPlayer(userIdStr);
        if (tableId != null) {
            Table table = tableManager.getTable(tableId);
            if (table != null) {
                table.findPlayerById(userIdStr).ifPresent(table::leaveTable);
            }
        }

        accountService.logout(uId);
        return ResponseEntity.ok("Logged out successfully");
    }

    @PatchMapping("/{id}/nickname")
    public Account changeNickname(@RequestHeader("Authorization") String authHeader,
                                  @PathVariable Long id,
                                  @RequestBody ChangeNicknameRequest request) {
        String token = extractToken(authHeader);
        accountService.validateSession(id, token);
        return accountService.changeNickname(id, request.newNickname());
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<String> changePassword(@RequestHeader("Authorization") String authHeader,
                                                 @PathVariable Long id,
                                                 @RequestBody ChangePasswordRequest request) {
        String token = extractToken(authHeader);
        accountService.validateSession(id, token);
        accountService.changePassword(id, request.oldPassword(), request.newPassword());
        return ResponseEntity.ok("Password updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAccount(@RequestHeader("Authorization") String authHeader,
                                                @PathVariable Long id) {
        String token = extractToken(authHeader);
        accountService.validateSession(id, token);
        accountService.deleteAccount(id);
        return ResponseEntity.ok("Account deleted successfully");
    }

    @GetMapping("/{id}/balance")
    public Map<String, Long> getBalance(@RequestHeader("Authorization") String authHeader,
                                        @PathVariable Long id) {
        String token = extractToken(authHeader);
        accountService.validateSession(id, token);
        Account account = accountService.findById(id);
        return Map.of("wallet_balance", account.getBalance());
    }
}