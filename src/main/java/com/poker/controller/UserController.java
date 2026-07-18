package com.poker.controller;

import com.poker.dto.UserStatsDTO;
import com.poker.persistence.entity.Account;
import com.poker.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserController {

    private final AccountService accountService;

    private String getAuthenticatedUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping("/{id}/stats")
    public UserStatsDTO getUserStats(@PathVariable Long id) {
        String authenticatedPrincipal = getAuthenticatedUserId();

        Account account = accountService.findById(id);

        if (!authenticatedPrincipal.equals(account.getNickname())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to view this profile");
        }

        return UserStatsDTO.fromAccount(account);
    }
}