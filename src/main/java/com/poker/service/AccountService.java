package com.poker.service;

import com.poker.dto.LoginResponseDTO;
import com.poker.exception.AccountNotFoundException;
import com.poker.exception.ChipAmountException;
import com.poker.exception.InvalidCredentialsException;
import com.poker.exception.InvalidInputException;
import com.poker.model.TransactionType;
import com.poker.persistence.entity.Account;
import com.poker.persistence.entity.GameTable;
import com.poker.persistence.entity.Transaction;
import com.poker.persistence.repository.AccountRepository;
import com.poker.persistence.repository.GameTableRepository;
import com.poker.persistence.repository.TransactionRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final GameTableRepository gameTableRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final Map<Long, String> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenToUserId = new ConcurrentHashMap<>();
    private final TableManager tableManager;

    private final long DAILY_BONUS_AMOUNT = 5000L;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          GameTableRepository gameTableRepository,
                          BCryptPasswordEncoder passwordEncoder,
                          @Lazy TableManager tableManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.gameTableRepository = gameTableRepository;
        this.passwordEncoder = passwordEncoder;
        this.tableManager = tableManager;
    }

    public void validateSession(Long userId, String token) {
        String sessionToken = activeSessions.get(userId);

        if (sessionToken == null || !sessionToken.equals(token)) {
            throw new InvalidCredentialsException("error.session.invalid");
        }
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

    @Transactional(readOnly = true)
    public List<Account> searchAccounts(String name) {
        return accountRepository.findByNicknameContaining(name);
    }

    public String getUserIdByToken(String token) {
        if (token == null) return null;

        Long userId = tokenToUserId.get(token);
        return userId != null ? String.valueOf(userId) : null;
    }

    @Transactional
    public LoginResponseDTO login(String login, String password) {
        Account account = accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException("Account with this login not found"));

        if (!passwordEncoder.matches(password, account.getPassword())) {
            throw new InvalidCredentialsException("error.password.incorrect");
        }

        String token = UUID.randomUUID().toString();
        activeSessions.put(account.getId(), token);
        tokenToUserId.put(token, account.getId());

        String userIdStr = account.getId().toString();

        if (tableManager.isPlayerActive(userIdStr)) {
            tableManager.forceKickPlayer(userIdStr);
        } else {
            Optional<Transaction> lastTx = transactionRepository.findFirstByAccountOrderByCreatedAtDesc(account);
            if (lastTx.isPresent()) {
                TransactionType lastType = lastTx.get().getType();

                if (lastType == TransactionType.BUY_IN || lastType == TransactionType.REBUY) {
                    long refundAmount = Math.abs(lastTx.get().getAmount());
                    account.setBalance(account.getBalance() + refundAmount);
                    Transaction refundLog = new Transaction(account, null, refundAmount, TransactionType.SYSTEM_REFUND);
                    transactionRepository.save(refundLog);
                }
            }
        }

        boolean bonusReceived = processDailyBonus(account);
        accountRepository.save(account);

        return LoginResponseDTO.fromAccount(account, token, bonusReceived);
    }

    public void logout(Long userId) {
        String token = activeSessions.remove(userId);
        if (token != null) {
            tokenToUserId.remove(token);
        }
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

        String token = UUID.randomUUID().toString();

        activeSessions.put(account.getId(), token);
        tokenToUserId.put(token, account.getId());

        return LoginResponseDTO.fromAccount(account, token, false);
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
            table = gameTableRepository.findById(java.util.UUID.fromString(tableId)).orElse(null);
        }

        account.setBalance(account.getBalance() - amount);
        accountRepository.save(account);

        Transaction tx = new Transaction(account, table, -amount, type);
        transactionRepository.save(tx);
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
    }
}