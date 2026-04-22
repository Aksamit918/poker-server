package com.poker.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final GameTableRepository gameTableRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                          GameTableRepository gameTableRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.gameTableRepository = gameTableRepository;
    }

    @Transactional(readOnly = true)
    public List<Account> searchAccounts(String name) {
        return accountRepository.findByNicknameContaining(name);
    }

    @Transactional
    public Account login(String login, String password) {
        Account account = accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException("Account with this login not found"));

        // TODO: BCrypt for hash checking
        if (!password.equals(account.getPassword())) {
            throw new InvalidCredentialsException("Invalid password");
        }

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

        return account;
    }

    @Transactional
    public Account register(String login, String password, String nickname) {
        if (accountRepository.findByLogin(login).isPresent()) {
            throw new IllegalArgumentException("Login is already taken");
        }

        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        Account account = new Account(login, password, nickname);
        return accountRepository.save(account);
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

        if (!account.getPassword().equals(oldPassword)) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }

        account.setPassword(newPassword);
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