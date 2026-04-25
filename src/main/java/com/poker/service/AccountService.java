package com.poker.service;

import com.poker.dto.LoginResponseDTO;
import com.poker.exception.AccountNotFoundException;
import com.poker.exception.ChipAmountException;
import com.poker.exception.InvalidCredentialsException;
import com.poker.model.TransactionType;
import com.poker.persistence.entity.Account;
import com.poker.persistence.entity.GameTable;
import com.poker.persistence.entity.Transaction;
import com.poker.persistence.repository.AccountRepository;
import com.poker.persistence.repository.GameTableRepository;
import com.poker.persistence.repository.TransactionRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          GameTableRepository gameTableRepository,
                          BCryptPasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.gameTableRepository = gameTableRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void validateSession(Long userId, String token) {
        String sessionToken = activeSessions.get(userId);

        if (sessionToken == null || !sessionToken.equals(token)) {
            throw new InvalidCredentialsException("Invalid or expired session. Please log in again.");
        }
    }

    @Transactional(readOnly = true)
    public List<Account> searchAccounts(String name) {
        return accountRepository.findByNicknameContaining(name);
    }

    @Transactional
    public LoginResponseDTO login(String login, String password) {
        Account account = accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException("Account with this login not found"));

        if (!passwordEncoder.matches(password, account.getPassword())) {
            throw new InvalidCredentialsException("Invalid password");
        }

        String token = UUID.randomUUID().toString();

        activeSessions.put(account.getId(), token);

        Optional<Transaction> lastTx = transactionRepository.findFirstByAccountOrderByCreatedAtDesc(account);
        if (lastTx.isPresent()) {
            if (lastTx.get().getType() == TransactionType.BUY_IN || lastTx.get().getType() == TransactionType.REBUY) {
                long refundAmount = Math.abs(lastTx.get().getAmount());
                account.setBalance(account.getBalance() + refundAmount);
                accountRepository.save(account);
                Transaction refundLog = new Transaction(account, null, refundAmount, TransactionType.SYSTEM_REFUND);
                transactionRepository.save(refundLog);
            }
        }

        return LoginResponseDTO.fromAccount(account, token);
    }

    public void logout(Long userId) {
        activeSessions.remove(userId);
    }

    @Transactional
    public LoginResponseDTO register(String login, String password, String nickname) {
        if (accountRepository.findByLogin(login).isPresent()) {
            throw new IllegalArgumentException("Login is already taken");
        }

        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        String encodedPassword = passwordEncoder.encode(password);

        Account account = new Account(login, encodedPassword, nickname);
        account = accountRepository.save(account);

        String token = UUID.randomUUID().toString();

        activeSessions.put(account.getId(), token);

        return LoginResponseDTO.fromAccount(account, token);
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
            throw new IllegalArgumentException("Nickname cannot be empty");
        }

        account.setNickname(newNickname);
        return accountRepository.save(account);
    }

    @Transactional
    public void changePassword(Long id, String oldPassword, String newPassword) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        if (!passwordEncoder.matches(oldPassword, account.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }

        String encodedNewPassword = passwordEncoder.encode(newPassword);
        account.setPassword(encodedNewPassword);
        accountRepository.save(account);
    }

    @Transactional
    public void deleteAccount(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new AccountNotFoundException("Account not found");
        }
        accountRepository.deleteById(id);
    }

    @Transactional
    public void withdrawFromWallet(Long accountId, long amount, String tableId, TransactionType type) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdraw amount must be greater than zero");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("User not found"));

        if (account.getBalance() < amount) {
            throw new ChipAmountException("Not enough money in wallet");
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
            throw new IllegalArgumentException("Deposit amount must be positive. Provided: " + amount);
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