package com.poker.service;

import com.poker.exception.AccountNotFoundException;
import com.poker.exception.ChipAmountException;
import com.poker.exception.InvalidCredentialsException;
import com.poker.persistence.entity.Account;
import com.poker.persistence.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<Account> searchAccounts(String name) {
        return accountRepository.findByNicknameContaining(name);
    }

    @Transactional(readOnly = true)
    public Account login(String login, String password) {
        Account account = accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException("Account with this login not found"));

        // TODO: BCrypt for hash checking
        if (!password.equals(account.getPassword())) {
            throw new InvalidCredentialsException("Invalid password");
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
    public void withdraw(Long accountId, long amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("User not found"));

        if (account.getBalance() < amount) {
            throw new ChipAmountException("Not enough money in wallet");
        }

        account.setBalance(account.getBalance() - amount);
        accountRepository.save(account);

        // TODO: transactionService.log(accountId, -amount, TransactionType.BUY_IN);
    }
}