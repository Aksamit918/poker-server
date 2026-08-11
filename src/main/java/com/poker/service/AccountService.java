package com.poker.service;

import com.poker.dto.LoginResponseDTO;
import com.poker.exception.AccountNotFoundException;
import com.poker.exception.ChipAmountException;
import com.poker.exception.InvalidCredentialsException;
import com.poker.exception.InvalidInputException;
import com.poker.model.TransactionType;
import com.poker.persistence.entity.Account;
import com.poker.persistence.entity.GameTable;
import com.poker.persistence.entity.RefreshToken;
import com.poker.persistence.entity.Transaction;
import com.poker.persistence.repository.AccountRepository;
import com.poker.persistence.repository.GameTableRepository;
import com.poker.persistence.repository.RefreshTokenRepository;
import com.poker.persistence.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final GameTableRepository gameTableRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final TableManager tableManager;
    private final GameEventPublisher eventPublisher;

    private final long refreshTokenExpirationMs;
    private final long DAILY_BONUS_AMOUNT = 5000L;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          GameTableRepository gameTableRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          JwtService jwtService,
                          @Value("${poker.jwt.refresh-expiration}") Duration refreshTokenDuration,
                          @Lazy TableManager tableManager,
                          GameEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.gameTableRepository = gameTableRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshTokenExpirationMs = refreshTokenDuration.toMillis();
        this.tableManager = tableManager;
        this.eventPublisher = eventPublisher;
    }

    private RefreshToken createRefreshToken(Account account) {
        RefreshToken refreshToken = refreshTokenRepository.findByAccount(account)
                .orElse(new RefreshToken());

        refreshToken.setAccount(account);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenExpirationMs));

        return refreshTokenRepository.saveAndFlush(refreshToken);
    }

    @Transactional
    public String refreshAccessToken(String requestRefreshToken) {
        log.info("[REFRESH] Request with token: {}", requestRefreshToken);

        return refreshTokenRepository.findByToken(requestRefreshToken)
                .map(token -> {
                    if (token.getExpiryDate().isBefore(Instant.now())) {
                        log.error("[REFRESH] Token expired! Expired at: {}", token.getExpiryDate());
                        refreshTokenRepository.delete(token);
                        throw new InvalidCredentialsException("error.refresh.expired");
                    }
                    return token;
                })
                .map(RefreshToken::getAccount)
                .map(account -> {
                    log.info("[REFRESH] Success! New Access token for: {}", account.getEmail());
                    return jwtService.generateToken(String.valueOf(account.getId()));
                })
                .orElseThrow(() -> {
                    log.error("[REFRESH] Token {} not found in DB!", requestRefreshToken);
                    return new InvalidCredentialsException("error.refresh.invalid");
                });
    }

    @Transactional
    public LoginResponseDTO authenticateWithGoogle(String googleId, String email, String name) {
        boolean isNewUser = false;
        Account account = accountRepository.findByGoogleId(googleId).orElse(null);

        if (account == null) {
            isNewUser = true;
            String safeNickname = (name == null || name.isBlank()) ? "Player_" + UUID.randomUUID().toString().substring(0, 5) : name;
            if (safeNickname.length() > 20) {
                safeNickname = safeNickname.substring(0, 20);
            }

            account = new Account();
            account.setGoogleId(googleId);
            account.setEmail(email);
            account.setNickname(safeNickname);

            account = accountRepository.save(account);
            log.info("Registered brand new player via Google: {}", email);
        }

        String userIdStr = account.getId().toString();

        try {
            if (tableManager.isPlayerActive(userIdStr)) {
                log.info("User {} is already active at a table. Force kicking to reset session...", userIdStr);
                tableManager.forceKickPlayer(userIdStr);
            }
        } catch (Exception e) {
            log.error("Failed to kick zombie player {}: {}", userIdStr, e.getMessage());
        }

        account = accountRepository.findById(account.getId())
                .orElseThrow(() -> new AccountNotFoundException("error.player.not.found"));

        String accessToken = jwtService.generateToken(userIdStr);
        RefreshToken refreshToken = createRefreshToken(account);

        boolean bonusReceived = processDailyBonus(account.getId());
        account = accountRepository.findById(account.getId())
                .orElseThrow(() -> new AccountNotFoundException("error.player.not.found"));

        return LoginResponseDTO.fromAccount(account, accessToken, refreshToken.getToken(), bonusReceived, isNewUser);
    }

    @Transactional
    public void saveAccount(Account account) {
        accountRepository.save(account);
    }

    @Transactional
    public void logout(Long userId) {
        Account account = accountRepository.findById(userId).orElse(null);
        if (account != null) {
            refreshTokenRepository.deleteByAccount(account);
        }
    }

    @Transactional(readOnly = true)
    public Account findById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("error.player.not.found"));
    }

    @Transactional
    public Account changeNickname(Long id, String newNickname) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("error.player.not.found"));

        if (newNickname == null || newNickname.isBlank() || newNickname.length() > 20) {
            throw new InvalidInputException("error.nickname.range", 1, 20);
        }

        account.setNickname(newNickname);
        return accountRepository.save(account);
    }

    @Transactional
    public void deleteAccount(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new AccountNotFoundException("error.account.not.found");
        }
        accountRepository.deleteById(id);
    }

    public void validateSession(Long userId, String token) {
        String tokenUserId = jwtService.extractUserId(token);
        if (tokenUserId == null) throw new InvalidCredentialsException("error.session.expired");
        if (!tokenUserId.equals(String.valueOf(userId))) throw new InvalidCredentialsException("error.session.invalid");
    }

    public String getUserIdByToken(String token) {
        return (token == null) ? null : jwtService.extractUserId(token);
    }

    @Transactional(readOnly = true)
    public List<Account> searchAccounts(String name) {
        return accountRepository.findByNicknameContaining(name);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void withdrawFromWallet(Long accountId, long amount, String tableId, TransactionType type) {
        if (amount <= 0) throw new InvalidInputException("error.amount.positive");
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("error.player.not.found"));
        if (account.getBalance() < amount) {
            throw new ChipAmountException("error.chips.insufficient", amount, account.getBalance());
        }

        int updated = accountRepository.withdrawBalance(accountId, amount);
        if (updated != 1) {
            Account latest = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException("error.player.not.found"));
            throw new ChipAmountException("error.chips.insufficient", amount, latest.getBalance());
        }

        GameTable table = resolveTable(tableId);
        account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("error.player.not.found"));
        transactionRepository.save(new Transaction(account, table, -amount, type));
        publishWalletUpdateSafe(accountId, account.getBalance(), type);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void depositToWallet(Long accountId, long amount, String tableId, TransactionType type) {
        if (amount < 0) throw new InvalidInputException("error.amount.deposit.positive", amount);
        if (amount == 0) {
            return;
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("error.player.not.found"));

        int updated = accountRepository.depositBalance(accountId, amount);
        if (updated != 1) {
            throw new AccountNotFoundException("error.player.not.found");
        }

        GameTable table = resolveTable(tableId);
        account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("error.player.not.found"));
        transactionRepository.save(new Transaction(account, table, amount, type));
        publishWalletUpdateSafe(accountId, account.getBalance(), type);
    }

    private GameTable resolveTable(String tableId) {
        if (tableId == null) return null;
        try {
            return gameTableRepository.findById(java.util.UUID.fromString(tableId)).orElse(null);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid table UUID: {}", tableId);
            return null;
        }
    }

    private void publishWalletUpdateSafe(Long accountId, long newBalance, TransactionType type) {
        try {
            eventPublisher.publishWalletUpdate(String.valueOf(accountId), newBalance, type.name());
        } catch (Exception e) {
            log.error("Failed to publish wallet update to Redis, but DB was saved. User: {}", accountId, e);
        }
    }

    private boolean processDailyBonus(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("error.player.not.found"));

        OffsetDateTime now = OffsetDateTime.now();
        boolean isFirstTime = (account.getLastBonusAt() == null);
        boolean isTimePassed = !isFirstTime && java.time.Duration.between(account.getLastBonusAt(), now).toHours() >= 24;

        if (isFirstTime || isTimePassed) {
            accountRepository.depositBalance(accountId, DAILY_BONUS_AMOUNT);
            accountRepository.updateLastBonusAt(accountId, now);
            Account refreshed = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException("error.player.not.found"));
            transactionRepository.save(new Transaction(refreshed, null, DAILY_BONUS_AMOUNT, TransactionType.DAILY_BONUS));
            publishWalletUpdateSafe(accountId, refreshed.getBalance(), TransactionType.DAILY_BONUS);
            return true;
        }
        return false;
    }

    @Transactional
    public void updatePlayerStats(String userId, boolean isWinner, long amountWon) {
        try {
            Long id = Long.parseLong(userId);
            if (!accountRepository.existsById(id)) return;

            int wonInc = isWinner ? 1 : 0;
            long wonAmount = isWinner ? amountWon : 0L;
            accountRepository.updateHandStats(id, wonInc, wonAmount);
        } catch (Exception e) {
            log.error("Failed to update stats for user {}", userId, e);
        }
    }
}