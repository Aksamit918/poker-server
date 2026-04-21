package com.poker.model;

import com.poker.exception.ChipAmountException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class Player {
    private final String userId;
    private String name;
    private volatile PlayerStatus status = PlayerStatus.WAITING;
    private AtomicLong chips;
    private AtomicLong walletBalance;
    private long roundContribution = 0;
    private long totalInHand = 0;
    private final List<Card> hand;

    public Player(String userId, String name, AtomicLong remainingWallet, AtomicLong chips) {
        this.userId = userId;
        this.name = name;
        this.walletBalance = remainingWallet;
        this.chips = chips;
        hand = new CopyOnWriteArrayList<>();
    }

    public void addCard(Card card) {
        hand.add(card);
    }
    public void clearHand() {
        hand.clear();
    }
    public long bet(long amount) {
        if (amount < 0) {
            return 0;
        }
        chips.updateAndGet(current -> {
            if (current < amount) {
                throw new ChipAmountException("Not enough chips. You must use ALL_IN action.");
            }
            return current - amount;
        });
        return amount;
    }
    public String getUserId() {
        return userId;
    }
    public String getName() {
        return name;
    }
    public AtomicLong getWalletBalance() {
        return walletBalance;
    }
    public AtomicLong getChips() {
        return chips;
    }
    public long getRoundContribution() {
        return roundContribution;
    }
    public void setRoundContribution(long roundContribution) {
        this.roundContribution = roundContribution;
    }
    public void addToRoundContribution(long amount) {
        this.roundContribution += amount;
    }
    public long getTotalInHand() {
        return totalInHand;
    }
    public void setTotalInHand(long totalInHand) {
        this.totalInHand = totalInHand;
    }
    public void addToTotalInHand(long totalInHand) {
        this.totalInHand += totalInHand;
    }
    public List<Card> getHand() {
        return hand;
    }
    public PlayerStatus getStatus() {
        return status;
    }
    public void setStatus(PlayerStatus status) {
        this.status = status;
    }
    public boolean canAct() {
        return status == PlayerStatus.ACTIVE ||
                status == PlayerStatus.CALLED ||
                status == PlayerStatus.RAISED ||
                status == PlayerStatus.CHECKED;
    }
    public boolean isInHand() {
        return status == PlayerStatus.ACTIVE ||
                status == PlayerStatus.CALLED ||
                status == PlayerStatus.RAISED ||
                status == PlayerStatus.CHECKED ||
                status == PlayerStatus.ALL_IN;
    }
    public boolean isEligibleForNewHand() {
        return status == PlayerStatus.WAITING;

    }

    @Override
    public String toString() {
        return userId + " " + name + "(" + chips + ") " + hand;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(userId, player.userId);
    }
    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
