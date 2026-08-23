package com.poker.controller;

import com.poker.dto.*;
import com.poker.model.Table;
import com.poker.persistence.entity.Account;
import com.poker.service.AccountService;
import com.poker.service.GoogleAuthService;
import com.poker.service.TableManager;
import com.poker.util.ImageFormatDetector;
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        Long userId = Long.parseLong(userIdStr);

        if (file == null || file.isEmpty()) {
            log.warn("Rejected avatar for user {}: empty file (contentType='{}', originalFilename='{}')",
                    userId, file == null ? null : file.getContentType(), file == null ? null : file.getOriginalFilename());
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        Path targetPath = null;
        try {
            byte[] data = file.getBytes();
            ImageFormatDetector.Format format = ImageFormatDetector.detect(data);
            if (format == null) {
                log.warn("Rejected avatar for user {}: not jpeg/png (contentType='{}', originalFilename='{}', size={})",
                        userId, file.getContentType(), file.getOriginalFilename(), data.length);
                return ResponseEntity.badRequest().body(Map.of("error", "Only JPEG and PNG images are allowed"));
            }

            if (!format.contentType().equals(file.getContentType())) {
                log.info("Accepted avatar for user {} by magic bytes (contentType='{}')", userId, file.getContentType());
            }

            String filename = userId + "_" + UUID.randomUUID().toString().substring(0, 8) + format.extension();
            targetPath = Paths.get(uploadDir).resolve(filename);
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, data);

            String previousFilename = accountService.replaceAvatarFilename(userId, filename);
            deleteAvatarFileIfPresent(previousFilename);

            String publicAvatarUrl = publicUrl + "/avatars/" + filename;
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "avatar_url", publicAvatarUrl,
                    "avatar_filename", filename
            ));
        } catch (Exception e) {
            if (targetPath != null) {
                try {
                    Files.deleteIfExists(targetPath);
                } catch (Exception cleanupError) {
                    log.warn("Failed to delete rejected avatar file {}", targetPath, cleanupError);
                }
            }
            log.error("Failed to upload avatar for user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to upload image"));
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

    private void deleteAvatarFileIfPresent(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path target = uploadRoot.resolve(filename).normalize();
            if (!target.startsWith(uploadRoot)) {
                log.warn("Refusing to delete avatar outside upload dir: {}", filename);
                return;
            }
            Files.deleteIfExists(target);
        } catch (Exception e) {
            log.warn("Failed to delete old avatar file: {}", filename);
        }
    }
}