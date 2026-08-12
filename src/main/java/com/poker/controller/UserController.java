package com.poker.controller;

import com.poker.dto.EmotePurchaseRequestDTO;
import com.poker.dto.EmotePurchaseResponseDTO;
import com.poker.dto.UserEmotesResponseDTO;
import com.poker.dto.UserStatsDTO;
import com.poker.persistence.entity.Account;
import com.poker.service.AccountService;
import com.poker.service.EmoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/user")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
@RequiredArgsConstructor
public class UserController {

    private final AccountService accountService;
    private final EmoteService emoteService;

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

    @GetMapping("/{id}/stats")
    public UserStatsDTO getUserStats(@PathVariable Long id) {
        Account account = accountService.findById(id);
        return UserStatsDTO.fromAccount(account);
    }

    @GetMapping("/{id}/emotes")
    public UserEmotesResponseDTO getUserEmotes(@PathVariable Long id) {
        verifyUserIdMatch(id);
        return emoteService.getUserEmotes(id);
    }

    @PostMapping("/{id}/emotes/purchase")
    public EmotePurchaseResponseDTO purchaseEmote(
            @PathVariable Long id,
            @Valid @RequestBody EmotePurchaseRequestDTO request
    ) {
        verifyUserIdMatch(id);
        return emoteService.purchase(id, request.emoteId());
    }
}
