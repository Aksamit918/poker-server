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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final TableManager tableManager;
    private final GoogleAuthService googleAuthService;

    @org.springframework.beans.factory.annotation.Value("${poker.avatars.directory}")
    private String uploadDir;

    @org.springframework.beans.factory.annotation.Value("${poker.public.url}")
    private String publicUrl;

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

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String userIdStr = getAuthenticatedUserId();
        if (userIdStr == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        Long userId = Long.parseLong(userIdStr);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        try {
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
                return ResponseEntity.badRequest().body("Only JPEG and PNG images are allowed");
            }

            String extension = contentType.equals("image/jpeg") ? ".jpg" : ".png";
            String filename = userId + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

            Path targetPath = Paths.get(uploadDir).resolve(filename);
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            Account account = accountService.findById(userId);

            if (account.getAvatarFilename() != null) {
                try {
                    Files.deleteIfExists(Paths.get(uploadDir).resolve(account.getAvatarFilename()));
                } catch (Exception e) {
                    log.warn("Failed to delete old avatar file: {}", account.getAvatarFilename());
                }
            }

            account.setAvatarFilename(filename);
            accountService.changeNickname(userId, account.getNickname());

            String publicAvatarUrl = publicUrl + "/avatars/" + filename;

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "avatar_url", publicAvatarUrl,
                    "avatar_filename", filename
            ));

        } catch (Exception e) {
            log.error("Failed to upload avatar for user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload image");
        }
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
    public ResponseEntity<Map<String, String>> changeNickname(@PathVariable Long id, @RequestBody ChangeNicknameRequest request) {
        verifyUserIdMatch(id);

        Account updatedAccount = accountService.changeNickname(id, request.newNickname());

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Nickname updated successfully",
                "nickname", updatedAccount.getNickname()
        ));
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