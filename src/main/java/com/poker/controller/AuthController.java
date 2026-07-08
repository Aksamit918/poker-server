package com.poker.controller;

import com.poker.dto.*;
import com.poker.model.Table;
import com.poker.persistence.entity.Account;
import com.poker.service.AccountService;
import com.poker.service.GoogleAuthService;
import com.poker.service.TableManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final TableManager tableManager;
    private final GoogleAuthService googleAuthService;

    private String getAuthenticatedUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }

        return String.valueOf(auth.getPrincipal()).trim();
    }

    private void verifyUserIdMatch(Long requestedId) {
        String authId = getAuthenticatedUserId();
        String reqIdStr = String.valueOf(requestedId).trim();

        if (authId == null || !authId.equals(reqIdStr)) {
            log.error("[AUTH ERROR] Match failed! Authenticated: '{}', Requested: '{}'", authId, reqIdStr);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "error.session.invalid");
        }
    }

    @PostMapping("/google")
    public LoginResponseDTO googleLogin(@RequestBody GoogleLoginRequest request) {
        GoogleAuthService.GoogleUser googleUser = googleAuthService.verifyToken(request.token());

        return accountService.authenticateWithGoogle(
                googleUser.getGoogleId(),
                googleUser.getEmail(),
                googleUser.getName()
        );
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