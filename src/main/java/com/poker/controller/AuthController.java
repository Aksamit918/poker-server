package com.poker.controller;

import com.poker.dto.LoginResponseDTO;
import com.poker.model.Table;
import com.poker.persistence.entity.Account;
import com.poker.service.AccountService;
import com.poker.service.TableManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final TableManager tableManager;

    public record RegisterRequest(String login, String password, String nickname) {}
    public record LoginRequest(String login, String password) {}
    public record ChangeNicknameRequest(String newNickname) {}
    public record ChangePasswordRequest(String oldPassword, String newPassword) {}
    public record RefreshRequestDTO(String refreshToken) {}
    public record RefreshResponseDTO(String accessToken) {}

    private String getAuthenticatedUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private void verifyUserIdMatch(Long requestedId) {
        if (!getAuthenticatedUserId().equals(String.valueOf(requestedId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Token does not match requested user ID");
        }
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
    public ResponseEntity<String> logout() {
        String userIdStr = getAuthenticatedUserId();
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
    public Account changeNickname(@PathVariable Long id, @RequestBody ChangeNicknameRequest request) {
        verifyUserIdMatch(id);
        return accountService.changeNickname(id, request.newNickname());
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<String> changePassword(@PathVariable Long id, @RequestBody ChangePasswordRequest request) {
        verifyUserIdMatch(id);
        accountService.changePassword(id, request.oldPassword(), request.newPassword());
        return ResponseEntity.ok("Password updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAccount(@PathVariable Long id) {
        verifyUserIdMatch(id);
        accountService.deleteAccount(id);
        return ResponseEntity.ok("Account deleted successfully");
    }

    @GetMapping("/{id}/balance")
    public Map<String, Long> getBalance(@PathVariable Long id) {
        verifyUserIdMatch(id);
        Account account = accountService.findById(id);
        return Map.of("wallet_balance", account.getBalance());
    }
}