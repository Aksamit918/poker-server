package com.poker.service;

import com.poker.dto.EmoteCatalogItemDTO;
import com.poker.dto.EmotePurchaseResponseDTO;
import com.poker.dto.UserEmotesResponseDTO;
import com.poker.exception.AlreadyOwned;
import com.poker.exception.EmoteNotFound;
import com.poker.exception.EmoteNotPurchasable;
import com.poker.exception.InsufficientFunds;
import com.poker.model.EmoteCatalog;
import com.poker.model.TransactionType;
import com.poker.persistence.entity.Account;
import com.poker.persistence.entity.Transaction;
import com.poker.persistence.entity.UserEmote;
import com.poker.persistence.repository.AccountRepository;
import com.poker.persistence.repository.TransactionRepository;
import com.poker.persistence.repository.UserEmoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmoteService {

    private final AccountRepository accountRepository;
    private final UserEmoteRepository userEmoteRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final GameEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public UserEmotesResponseDTO getUserEmotes(Long userId) {
        Account account = accountService.findById(userId);
        Set<String> owned = resolveOwnedEmoteIds(userId);

        List<EmoteCatalogItemDTO> catalog = EmoteCatalog.all().stream()
                .filter(EmoteCatalog.EmoteDefinition::active)
                .map(def -> new EmoteCatalogItemDTO(
                        def.emoteId(),
                        def.price(),
                        def.isDefault(),
                        owned.contains(def.emoteId())
                ))
                .toList();

        return new UserEmotesResponseDTO(account.getBalance(), catalog, List.copyOf(owned));
    }

    @Transactional
    public EmotePurchaseResponseDTO purchase(Long userId, String emoteId) {
        EmoteCatalog.EmoteDefinition definition = EmoteCatalog.find(emoteId)
                .filter(EmoteCatalog.EmoteDefinition::active)
                .orElseThrow(() -> new EmoteNotFound("error.emote.not.found"));

        if (definition.isDefault()) {
            throw new EmoteNotPurchasable("error.emote.not.purchasable");
        }

        if (userEmoteRepository.existsByUserIdAndEmoteId(userId, definition.emoteId())) {
            throw new AlreadyOwned("error.emote.already.owned");
        }

        Account account = accountService.findById(userId);
        long price = definition.price();
        if (account.getBalance() < price) {
            throw new InsufficientFunds("error.emote.insufficient.funds");
        }

        int updated = accountRepository.withdrawBalance(userId, price);
        if (updated != 1) {
            throw new InsufficientFunds("error.emote.insufficient.funds");
        }

        try {
            userEmoteRepository.saveAndFlush(new UserEmote(userId, definition.emoteId()));
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyOwned("error.emote.already.owned");
        }

        Account refreshed = accountService.findById(userId);
        transactionRepository.save(new Transaction(refreshed, null, -price, TransactionType.EMOTE_PURCHASE));
        publishWalletUpdateSafe(userId, refreshed.getBalance());

        return new EmotePurchaseResponseDTO(
                "success",
                definition.emoteId(),
                price,
                refreshed.getBalance(),
                List.copyOf(resolveOwnedEmoteIds(userId))
        );
    }

    @Transactional(readOnly = true)
    public boolean canSendEmote(Long userId, String emoteId) {
        return EmoteCatalog.find(emoteId)
                .filter(EmoteCatalog.EmoteDefinition::active)
                .map(def -> def.isDefault() || userEmoteRepository.existsByUserIdAndEmoteId(userId, def.emoteId()))
                .orElse(false);
    }

    private Set<String> resolveOwnedEmoteIds(Long userId) {
        Set<String> owned = new LinkedHashSet<>(EmoteCatalog.defaultEmoteIds());
        userEmoteRepository.findByUserId(userId).stream()
                .map(UserEmote::getEmoteId)
                .forEach(owned::add);
        return owned;
    }

    private void publishWalletUpdateSafe(Long accountId, long newBalance) {
        try {
            eventPublisher.publishWalletUpdate(String.valueOf(accountId), newBalance, TransactionType.EMOTE_PURCHASE.name());
        } catch (Exception e) {
            log.error("Failed to publish wallet update after emote purchase. User: {}", accountId, e);
        }
    }
}
