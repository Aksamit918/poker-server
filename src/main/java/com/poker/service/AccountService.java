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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final GameTableRepository gameTableRepository;
    private final RefreshTokenRepository refreshTokenRepository; // Добавили
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService; // Добавили
    private final TableManager tableManager;
    private final GameEventPublisher eventPublisher;

    private final long refreshTokenExpirationMs;
    private final java.time.OffsetDateTime serverStartTime = java.time.OffsetDateTime.now();
    private final long DAILY_BONUS_AMOUNT = 5000L;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          GameTableRepository gameTableRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          BCryptPasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          @Value("${poker.jwt.refresh-expiration}") Duration refreshTokenDuration,
                          @Lazy TableManager tableManager,
                          GameEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.gameTableRepository = gameTableRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenExpirationMs = refreshTokenDuration.toMillis();
        this.tableManager = tableManager;
        this.eventPublisher = eventPublisher;
    }

    private RefreshToken createRefreshToken(Account account) {
        refreshTokenRepository.deleteByAccount(account);
        refreshTokenRepository.flush();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAccount(account);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenExpirationMs));

        return refreshTokenRepository.saveAndFlush(refreshToken);
    }

    @Transactional
    public String refreshAccessToken(String requestRefreshToken) {
        return refreshTokenRepository.findByToken(requestRefreshToken)
                .map(token -> {
                    if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
                        refreshTokenRepository.delete(token);
                        throw new InvalidCredentialsException("error.refresh.expired");
                    }
                    return token;
                })
                .map(RefreshToken::getAccount)
                .map(account -> jwtService.generateToken(String.valueOf(account.getId())))
                .orElseThrow(() -> new InvalidCredentialsException("error.refresh.invalid"));
    }

    public void validateSession(Long userId, String token) {
        String tokenUserId = jwtService.extractUserId(token);

        if (tokenUserId == null) {
            throw new InvalidCredentialsException("error.session.expired");
        }

        if (!tokenUserId.equals(String.valueOf(userId))) {
            throw new InvalidCredentialsException("error.session.invalid");
        }
    }

    public String getUserIdByToken(String token) {
        if (token == null) {
            return null;
        }

        return jwtService.extractUserId(token);
    }

    @Transactional(readOnly = true)
    public List<Account> searchAccounts(String name) {
        return accountRepository.findByNicknameContaining(name);
    }

    @Transactional
    public LoginResponseDTO register(String login, String password, String nickname) {
        if (accountRepository.findByLogin(login).isPresent()) {
            throw new IllegalArgumentException("error.login.taken");
        }

        if (password == null || password.isBlank() || password.length() < 6) {
            throw new InvalidInputException("error.password.length", 6);
        }

        if (nickname == null || nickname.isBlank() || nickname.length() > 20) {
            throw new InvalidInputException("error.nickname.range", 1, 20);
        }

        String encodedPassword = passwordEncoder.encode(password);
        Account account = new Account(login, encodedPassword, nickname);
        account = accountRepository.save(account);

        String accessToken = jwtService.generateToken(String.valueOf(account.getId()));
        RefreshToken refreshToken = createRefreshToken(account);

        return LoginResponseDTO.fromAccount(account, accessToken, refreshToken.getToken(), false);
    }

    @Transactional
    public LoginResponseDTO login(String login, String password) {
        Account account = accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException("error.account.not.found"));

        if (!passwordEncoder.matches(password, account.getPassword())) {
            throw new InvalidCredentialsException("error.password.incorrect");
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

        String accessToken = jwtService.generateToken(userIdStr);
        RefreshToken refreshToken = createRefreshToken(account);

        boolean bonusReceived = processDailyBonus(account);
        accountRepository.save(account);

        return LoginResponseDTO.fromAccount(account, accessToken, refreshToken.getToken(), bonusReceived);
    }

    public void logout(Long userId) {
        Account account = accountRepository.findById(userId).orElse(null);
        if (account != null) {
            refreshTokenRepository.deleteByAccount(account);
        }
    }

    @Transactional(readOnly = true)
    public Account findById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("User not found"));
    }

    @Transactional
    public Account changeNickname(Long id, String newNickname) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        if (newNickname == null || newNickname.isBlank()) {
            throw new InvalidInputException("error.nickname.empty");
        }

        account.setNickname(newNickname);
        return accountRepository.save(account);
    }

    @Transactional
    public void changePassword(Long id, String oldPassword, String newPassword) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        if (!passwordEncoder.matches(oldPassword, account.getPassword())) {
            throw new InvalidCredentialsException("error.password.incorrect");
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new InvalidInputException("error.password.length", 6);
        }

        String encodedNewPassword = passwordEncoder.encode(newPassword);
        account.setPassword(encodedNewPassword);
        accountRepository.save(account);
    }

    @Transactional
    public void deleteAccount(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new AccountNotFoundException("error.account.not.found");
        }
        accountRepository.deleteById(id);
    }

    @Transactional
    public void withdrawFromWallet(Long accountId, long amount, String tableId, TransactionType type) {
        if (amount <= 0) {
            throw new InvalidInputException("error.amount.positive");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("User not found"));

        if (account.getBalance() < amount) {
            throw new ChipAmountException("error.chips.insufficient", amount, account.getBalance());
        }

        GameTable table = null;
        if (tableId != null) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(tableId);
                table = gameTableRepository.findById(uuid).orElse(null);
            } catch (IllegalArgumentException e) {

            }
        }

        account.setBalance(account.getBalance() - amount);
        accountRepository.save(account);

        Transaction tx = new Transaction(account, table, -amount, type);
        transactionRepository.save(tx);

        eventPublisher.publishWalletUpdate(String.valueOf(accountId), account.getBalance(), type.name());
    }

    @Transactional
    public void depositToWallet(Long accountId, long amount, String tableId, TransactionType type) {
        if (amount == 0) {
            return;
        }

        if (amount < 0) {
            throw new InvalidInputException("error.amount.deposit.positive", amount);
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("User not found"));

        GameTable table = null;
        if (tableId != null) {
            table = gameTableRepository.findById(java.util.UUID.fromString(tableId)).orElse(null);
        }

        account.setBalance(account.getBalance() + amount);
        accountRepository.save(account);

        Transaction tx = new Transaction(account, table, amount, type);
        transactionRepository.save(tx);

        eventPublisher.publishWalletUpdate(String.valueOf(accountId), account.getBalance(), type.name());
    }

    private boolean processDailyBonus(Account account) {
        OffsetDateTime now = OffsetDateTime.now();

        boolean isFirstTime = (account.getLastBonusAt() == null);

        boolean isTimePassed = !isFirstTime && java.time.Duration.between(account.getLastBonusAt(), now).toHours() >= 24;

        if (isFirstTime || isTimePassed) {
            account.setBalance(account.getBalance() + DAILY_BONUS_AMOUNT);
            account.setLastBonusAt(now);

            transactionRepository.save(new Transaction(
                    account,
                    null,
                    DAILY_BONUS_AMOUNT,
                    TransactionType.DAILY_BONUS)
            );
            return true;
        }

        return false;
    }
}