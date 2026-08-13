package com.poker.model;

import com.poker.dto.ShowdownPayoutDTO;
import com.poker.dto.events.StreetEndDTO;
import com.poker.exception.TableFullException;
import com.poker.exception.*;
import com.poker.util.TableEventListener;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;

public class Table {
    private final java.util.Queue<BufferedEvent> recentEvents = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final long EVENT_TTL_MS = 60000;
    private final Object lock = new Object();
    private final TableEventListener eventListener;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> currentTimer;
    private ScheduledFuture<?> pendingStartTask;
    private ScheduledFuture<?> pendingStreetTask;
    private ScheduledFuture<?> pendingNextHandCleanupTask;
    private ScheduledFuture<?> pendingNextHandStartTask;
    private long handScheduleEpoch = 0;
    private static final int TURN_TIMEOUT = 15;
    private static final int STAGE_TRANSITION_DELAY = 2;
    private static final int REBUY_TIMEOUT = 30;
    private static final int SHOWDOWN_BASE_DELAY = 10;
    private static final int SHOWDOWN_LAYER_DELAY = 3;
    private static final long REBUY_GRACE_PERIOD_MS = 3500;
    private static final int START_GAME_DELAY = 3;
    private static final int PREMATURE_END_DELAY = 4;
    private static final int KICK_OUT_OF_MONEY_DELAY = 5;
    private long turnStartTime;
    private final String id;
    private String name;
    private final boolean isPrivate;
    private final String passcode;
    private volatile boolean isTransitioning = false;
    private final int MAX_PLAYERS;
    private final int MIN_PLAYERS;
    private final long minBuyIn;
    private final long maxBuyIn;
    private volatile TableStates state;
    private final List<Player> players;
    private int activePlayerIdx;
    private int dealerIdx;
    private int smallBlindIdx;
    private int bigBlindIdx;
    private Deck deck;
    private final AtomicReferenceArray<Card> communityCards;
    private AtomicLong pot;
    private long lastRaiseStep = 0;
    private long currentMaxBet = 0;
    private final long smallBlindBet;
    private final long bigBlindBet;
    private List<ShowdownPayoutDTO> lastShowdownPayouts = new ArrayList<>();

    public Table(String id, String name, long smallBlindBet, long bigBlindBet, int MIN_PLAYERS,
                 int MAX_PLAYERS, long minBuyIn, boolean isPrivate, String passcode,
                 TableEventListener eventListener, ScheduledExecutorService scheduler) {
        this.id = id;
        this.name = name;
        this.isPrivate = isPrivate;
        this.passcode = passcode;
        this.MIN_PLAYERS = MIN_PLAYERS;
        this.MAX_PLAYERS = MAX_PLAYERS;
        this.minBuyIn = minBuyIn;
        this.maxBuyIn = bigBlindBet * 100;
        this.players = new CopyOnWriteArrayList<>();
        this.deck = new Deck();
        this.communityCards = new AtomicReferenceArray<>(5);
        this.pot = new AtomicLong(0);
        this.state = TableStates.WAITING_FOR_PLAYERS;
        this.smallBlindBet = smallBlindBet;
        this.bigBlindBet = bigBlindBet;
        this.dealerIdx = -1;
        this.activePlayerIdx = -1;
        this.eventListener = eventListener;
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    private void addCommunityCard(Card card) {
        for (int i = 0; i < communityCards.length(); i++) {
            if (communityCards.compareAndSet(i, null, card)) {
                return;
            }
        }
        throw new IllegalStateException("Community cards already dealt");
    }
    private void clearCommunityCards() {
        for (int i = 0; i < communityCards.length(); i++) {
            communityCards.set(i, null);
        }
    }
    public List<Card> getCommunityCards() {
        List<Card> cards = new ArrayList<>(5);
        for (int i = 0; i < communityCards.length(); i++) {
            Card c = communityCards.get(i);
            if (c != null) {
                cards.add(c);
            }
        }
        return List.copyOf(cards);
    }

    private void setupPositions() {
        if (this.dealerIdx == -1 || getPlayerBySeat(dealerIdx) == null) {
            Player firstActive = players.stream()
                    .filter(p -> p.getStatus() == PlayerStatus.ACTIVE)
                    .findFirst().orElse(null);
            if (firstActive != null) this.dealerIdx = firstActive.getSeatIndex();
        } else {
            this.dealerIdx = getNextActivePlayerSeat(dealerIdx);
        }

        this.smallBlindIdx = getNextActivePlayerSeat(dealerIdx);
        this.bigBlindIdx = getNextActivePlayerSeat(smallBlindIdx);

        this.activePlayerIdx = getNextActivePlayerSeat(bigBlindIdx);
    }

    private void cancelFuture(ScheduledFuture<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
    }
    private void cancelHandLifecycleTasks() {
        cancelFuture(pendingStartTask);
        cancelFuture(pendingStreetTask);
        cancelFuture(pendingNextHandCleanupTask);
        cancelFuture(pendingNextHandStartTask);
        pendingStartTask = null;
        pendingStreetTask = null;
        pendingNextHandCleanupTask = null;
        pendingNextHandStartTask = null;
    }
    private long beginHandScheduleEpoch() {
        cancelHandLifecycleTasks();
        return ++handScheduleEpoch;
    }

    private void startNewHand() {
        try {
            synchronized (lock) {
                if (this.state != TableStates.WAITING_FOR_PLAYERS) {
                    return;
                }

                this.isTransitioning = false;

                for (Player p : players) {
                    p.clearHand();
                    if (p.getStatus() == PlayerStatus.WAITING && p.getChips().get() >= bigBlindBet) {
                        p.setStatus(PlayerStatus.ACTIVE);
                        p.setRoundContribution(0);
                        p.setTotalInHand(0);
                    }
                }

                long activeCount = players.stream().filter(p -> p.getStatus() == PlayerStatus.ACTIVE).count();
                if (activeCount < 2) {
                    this.state = TableStates.WAITING_FOR_PLAYERS;
                    if (eventListener != null) eventListener.onTableUpdate(this);
                    return;
                }

                setupPositions();

                Player sbPlayer = getPlayerBySeat(smallBlindIdx);
                Player bbPlayer = getPlayerBySeat(bigBlindIdx);

                if (sbPlayer == null || bbPlayer == null) {
                    setupPositions();
                    sbPlayer = getPlayerBySeat(smallBlindIdx);
                    bbPlayer = getPlayerBySeat(bigBlindIdx);
                }

                long sbPaid = sbPlayer.bet(smallBlindBet);
                long bbPaid = bbPlayer.bet(bigBlindBet);

                pot.set(sbPaid + bbPaid);

                sbPlayer.setRoundContribution(sbPaid);
                sbPlayer.setTotalInHand(sbPaid);
                bbPlayer.setRoundContribution(bbPaid);
                bbPlayer.setTotalInHand(bbPaid);

                if (sbPlayer.getChips().get() == 0) sbPlayer.setStatus(PlayerStatus.ALL_IN);
                if (bbPlayer.getChips().get() == 0) bbPlayer.setStatus(PlayerStatus.ALL_IN);

                this.currentMaxBet = bigBlindBet;
                this.lastRaiseStep = bigBlindBet;

                for (Player p : players) {
                    if (p != sbPlayer && p != bbPlayer) {
                        p.setRoundContribution(0);
                        p.setTotalInHand(0);
                    }
                }

                clearCommunityCards();
                this.deck = new Deck();
                dealCards();

                this.state = TableStates.PRE_FLOP;

                if (eventListener != null) {
                    eventListener.onTableUpdate(this);
                }
                startTimer();

                System.out.println("DEBUG: Hand started. Pot: " + pot.get() + ", MaxBet: " + currentMaxBet);
            }
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR IN startNewHand: " + e.getMessage());
            e.printStackTrace();
            this.isTransitioning = false;
        }
    }
    private void scheduleNextHand(int delayInSeconds) {
        this.isTransitioning = true;
        final long epoch = beginHandScheduleEpoch();

        pendingNextHandCleanupTask = scheduler.schedule(() -> {
            try {
                synchronized (lock) {
                    if (epoch != handScheduleEpoch) {
                        return;
                    }

                    cleanupTable();

                    if (eventListener != null) {
                        eventListener.onTableUpdate(this);
                    }

                    pendingNextHandStartTask = scheduler.schedule(() -> {
                        synchronized (lock) {
                            if (epoch != handScheduleEpoch) {
                                return;
                            }

                            long readyCount = players.stream()
                                    .filter(p -> p.getStatus() == PlayerStatus.WAITING && p.getChips().get() >= bigBlindBet)
                                    .count();

                            if (readyCount >= MIN_PLAYERS && state == TableStates.WAITING_FOR_PLAYERS) {
                                startNewHand();
                            } else if (state == TableStates.WAITING_FOR_PLAYERS) {
                                this.isTransitioning = false;
                                if (eventListener != null) {
                                    eventListener.onTableUpdate(this);
                                }
                                System.out.println("DEBUG: Hand skipped. Still waiting for players.");
                            }
                        }
                    }, REBUY_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                System.err.println("CRITICAL ERROR IN scheduleNextHand: " + e.getMessage());
                e.printStackTrace();
                synchronized (lock) {
                    if (epoch == handScheduleEpoch) {
                        this.isTransitioning = false;
                    }
                }
            }
        }, delayInSeconds, TimeUnit.SECONDS);
    }
    private void setTableState(TableStates state) {
        this.state = state;
    }
    private void dealCards() {
        for (Player player : players) {
            if (player.getStatus() == PlayerStatus.ACTIVE || player.getStatus() == PlayerStatus.ALL_IN) {
                player.clearHand();
                player.addCard(deck.drawCard());
                player.addCard(deck.drawCard());
            }
        }
    }
    private void dealFlop() {
        synchronized (lock) {
            addCommunityCard(deck.drawCard());
            addCommunityCard(deck.drawCard());
            addCommunityCard(deck.drawCard());
        }
    }
    private void dealTurn() {
        synchronized (lock) {
            addCommunityCard(deck.drawCard());
        }
    }
    private void dealRiver() {
        synchronized (lock) {
            addCommunityCard(deck.drawCard());
        }
    }

    private Player getPlayerBySeat(int seatIndex) {
        return players.stream()
                .filter(p -> p.getSeatIndex() == seatIndex)
                .findFirst()
                .orElse(null);
    }
    private boolean isPlayerTurn(Player player) {
        return player.equals(this.getPlayerBySeat(activePlayerIdx));
    }
    private boolean advanceTurn() {
        int nextIdx = activePlayerIdx;
        for (int i = 0; i < players.size(); i++) {
            nextIdx = getNextActivePlayerSeat(nextIdx);
            Player p = getPlayerBySeat(nextIdx);
            if (p != null && p.getStatus() == PlayerStatus.ACTIVE) {
                activePlayerIdx = nextIdx;
                return true;
            }
        }
        this.activePlayerIdx = -1;
        return false;
    }

    private void updateStatusAfterBet(Player player) {
        if (player.getChips().get() == 0) {
            player.setStatus(PlayerStatus.ALL_IN);
        } else {
            player.setStatus(PlayerStatus.CALLED);
        }
    }
    private void processFold(Player player) {
        player.setStatus(PlayerStatus.FOLDED);
    }
    private void processCall(Player player) {
        long amountToCall = currentMaxBet - player.getRoundContribution();
        if (player.getChips().get() < amountToCall) {
            processAllIn(player);
            return;
        }
        if (amountToCall <= 0) {
            throw new IllegalCallException("error.illegal.call");
        }
        long actualPaid = player.bet(amountToCall);
        pot.addAndGet(actualPaid);
        player.addToRoundContribution(actualPaid);
        player.addToTotalInHand(actualPaid);
        updateStatusAfterBet(player);
    }
    private long maxCoverableBet(Player actor) {
        return players.stream()
                .filter(p -> p != actor && p.isInHand())
                .mapToLong(p -> p.getRoundContribution() + p.getChips().get())
                .max()
                .orElse(currentMaxBet);
    }

    private void matchCurrentBet(Player player) {
        if (player.getRoundContribution() < currentMaxBet) {
            processCall(player);
        } else {
            processCheck(player);
        }
    }

    private void processRaise(Player player, long newMaxBet) {
        long maxCoverable = maxCoverableBet(player);
        if (newMaxBet > maxCoverable) {
            newMaxBet = maxCoverable;
        }

        long minAllowedRaise = this.currentMaxBet + this.lastRaiseStep;
        long amountToRaise = newMaxBet - player.getRoundContribution();
        boolean isAllIn = amountToRaise >= player.getChips().get();

        if (newMaxBet <= currentMaxBet) {
            matchCurrentBet(player);
            return;
        }

        if (!isAllIn && newMaxBet < minAllowedRaise) {
            if (maxCoverable < minAllowedRaise) {
                matchCurrentBet(player);
                return;
            }
            throw new IllegalRaiseException("error.illegal.raise.too.low", minAllowedRaise);
        }

        if (amountToRaise <= 0) {
            throw new IllegalRaiseException("error.illegal.raise.invalid", currentMaxBet);
        }

        if (isAllIn) {
            processAllIn(player);
            return;
        }

        this.lastRaiseStep = newMaxBet - this.currentMaxBet;
        this.currentMaxBet = newMaxBet;

        long actualPaid = player.bet(amountToRaise);
        pot.addAndGet(actualPaid);
        player.addToRoundContribution(actualPaid);
        player.addToTotalInHand(actualPaid);
        updateStatusAfterBet(player);

        for (Player p : players) {
            if (p != player &&
                    p.getStatus() != PlayerStatus.FOLDED &&
                    p.getStatus() != PlayerStatus.ALL_IN &&
                    p.getStatus() != PlayerStatus.WAITING &&
                    p.getStatus() != PlayerStatus.SITTING_OUT) {
                p.setStatus(PlayerStatus.ACTIVE);
            }
        }
    }
    private void processCheck(Player player) {
        if (player.getRoundContribution() < currentMaxBet) {
            throw new IllegalCheckException("error.illegal.check", currentMaxBet);
        }
        player.setStatus(PlayerStatus.CHECKED);
    }
    private void processAllIn(Player player) {
        long available = player.getChips().get();
        long maxPutIn = Math.max(0L, maxCoverableBet(player) - player.getRoundContribution());
        long chips = Math.min(available, maxPutIn);
        if (chips <= 0) {
            if (player.getRoundContribution() >= currentMaxBet) {
                processCheck(player);
            }
            return;
        }

        pot.addAndGet(chips);
        player.getChips().addAndGet(-chips);
        player.addToRoundContribution(chips);
        player.addToTotalInHand(chips);
        updateStatusAfterBet(player);

        if (player.getRoundContribution() > currentMaxBet) {

            long raisedAmount = player.getRoundContribution() - currentMaxBet;
            if (raisedAmount >= this.lastRaiseStep) {
                this.lastRaiseStep = raisedAmount;
            }

            this.currentMaxBet = player.getRoundContribution();

            for (Player p : players) {
                if (p != player &&
                        p.getStatus() != PlayerStatus.FOLDED &&
                        p.getStatus() != PlayerStatus.ALL_IN &&
                        p.getStatus() != PlayerStatus.WAITING &&
                        p.getStatus() != PlayerStatus.SITTING_OUT) {
                    p.setStatus(PlayerStatus.ACTIVE);
                }
            }
        }
    }
    public void rebuy(Player player, long amount, long walletBalance) {
        synchronized (lock) {
            if (player.getStatus() != PlayerStatus.WAITING &&
                    player.getStatus() != PlayerStatus.FOLDED &&
                    player.getStatus() != PlayerStatus.SITTING_OUT) {
                throw new IllegalTableStateException("error.rebuy.active.hand");
            }

            long currentChips = player.getChips().get();
            long increasedChips = currentChips + amount;

            if (increasedChips > maxBuyIn) {
                throw new ChipAmountException("error.chips.max.rebuy", maxBuyIn);
            }

            if (increasedChips < bigBlindBet) {
                throw new ChipAmountException("error.chips.min.rebuy", bigBlindBet);
            }

            player.getWalletBalance().set(walletBalance);
            player.getChips().addAndGet(amount);

            if (player.getStatus() == PlayerStatus.SITTING_OUT) {
                player.setStatus(PlayerStatus.WAITING);
                player.setSitOutDeadline(0L);
            }

            long readyToPlay = players.stream()
                    .filter(p -> p.getStatus() == PlayerStatus.WAITING && p.getChips().get() >= bigBlindBet)
                    .count();

            if (readyToPlay >= MIN_PLAYERS && state == TableStates.WAITING_FOR_PLAYERS && !isTransitioning) {
                startNewHand();
            }

            if (eventListener != null) {
                eventListener.onTableUpdate(this);
            }
        }
    }

    private void endBettingRound() {
        synchronized (lock) {
            if (isTransitioning) {
                return;
            }
            this.isTransitioning = true;
            stopTimer();

            List<Player> sortedByContrib = players.stream()
                    .filter(p -> p.getRoundContribution() > 0)
                    .sorted((p1, p2) -> Long.compare(p2.getRoundContribution(), p1.getRoundContribution()))
                    .toList();

            if (!sortedByContrib.isEmpty()) {
                Player topPlayer = sortedByContrib.get(0);
                long maxContrib = topPlayer.getRoundContribution();
                long secondMax = (sortedByContrib.size() > 1) ? sortedByContrib.get(1).getRoundContribution() : 0;

                long refund = maxContrib - secondMax;
                if (refund > 0) {
                    topPlayer.getChips().addAndGet(refund);
                    topPlayer.setRoundContribution(maxContrib - refund);
                    topPlayer.setTotalInHand(topPlayer.getTotalInHand() - refund);
                    pot.addAndGet(-refund);
                    this.currentMaxBet = secondMax;
                }
            }

            final TableStates stateWhenScheduled = this.state;
            cancelFuture(pendingStreetTask);
            pendingStreetTask = scheduler.schedule(() -> {
                try {
                    synchronized (lock) {
                        if (this.state != stateWhenScheduled) {
                            return;
                        }
                        if (this.state != TableStates.PRE_FLOP
                                && this.state != TableStates.FLOP
                                && this.state != TableStates.TURN
                                && this.state != TableStates.RIVER) {
                            return;
                        }

                        this.isTransitioning = false;

                        String previousState = this.state.name();
                        List<StreetEndDTO.PlayerContributionDTO> contributions = players.stream()
                                .filter(p -> p.getRoundContribution() > 0)
                                .map(p -> new StreetEndDTO.PlayerContributionDTO(p.getUserId(), p.getRoundContribution()))
                                .toList();

                        switch (this.state) {
                            case PRE_FLOP -> { setTableState(TableStates.FLOP); dealFlop(); }
                            case FLOP -> { setTableState(TableStates.TURN); dealTurn(); }
                            case TURN -> { setTableState(TableStates.RIVER); dealRiver(); }
                            case RIVER -> setTableState(TableStates.SHOWDOWN);
                        }

                        StreetEndDTO streetEndEvent = StreetEndDTO.createStreetEndDTO(this, previousState, contributions);
                        if (eventListener != null) {
                            eventListener.onStreetEnd(streetEndEvent);
                        }

                        this.currentMaxBet = 0;
                        this.lastRaiseStep = bigBlindBet;

                        for (Player p : players) {
                            p.setRoundContribution(0);
                            if (p.getStatus() != PlayerStatus.FOLDED &&
                                    p.getStatus() != PlayerStatus.ALL_IN &&
                                    p.getStatus() != PlayerStatus.WAITING &&
                                    p.getStatus() != PlayerStatus.SITTING_OUT) {
                                p.setStatus(PlayerStatus.ACTIVE);
                            }
                        }

                        if (eventListener != null && this.state != TableStates.SHOWDOWN) {
                            eventListener.onTableUpdate(this);
                        }

                        if (this.state == TableStates.SHOWDOWN) {
                            int layers = distributePot();
                            pot.set(0);

                            if (eventListener != null) {
                                eventListener.onTableUpdate(this);
                            }

                            int totalDelay = (layers * SHOWDOWN_LAYER_DELAY) + SHOWDOWN_BASE_DELAY;
                            scheduleNextHand(totalDelay);

                        } else {
                            long stillCanBet = players.stream()
                                    .filter(p -> p.getStatus() != PlayerStatus.FOLDED &&
                                            p.getStatus() != PlayerStatus.ALL_IN &&
                                            p.getStatus() != PlayerStatus.WAITING)
                                    .count();

                            if (stillCanBet < 2) {
                                endBettingRound();
                            } else {
                                this.activePlayerIdx = dealerIdx;
                                advanceTurn();
                                startTimer();

                                if (eventListener != null) {
                                    eventListener.onTableUpdate(this);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("FATAL ERROR IN endBettingRound: " + e.getMessage());
                    e.printStackTrace();
                    synchronized (lock) {
                        if (this.state == stateWhenScheduled
                                || this.state == TableStates.FLOP
                                || this.state == TableStates.TURN
                                || this.state == TableStates.RIVER
                                || this.state == TableStates.SHOWDOWN) {
                            this.isTransitioning = false;
                        }
                    }
                }
            }, STAGE_TRANSITION_DELAY, TimeUnit.SECONDS);
        }
    }
    private void finishHandPrematurely() {
        synchronized (lock) {
            if (this.state == TableStates.CLEANUP || this.state == TableStates.WAITING_FOR_PLAYERS) {
                return;
            }

            stopTimer();
            cancelFuture(pendingStreetTask);
            pendingStreetTask = null;

            this.isTransitioning = true;
            this.state = TableStates.CLEANUP;

            List<Player> winners = players.stream()
                    .filter(Player::isInHand)
                    .toList();

            lastShowdownPayouts.clear();

            if (!winners.isEmpty()) {
                Player winner = winners.get(0);

                long winnerTotal = winner.getTotalInHand();
                long secondMaxTotal = players.stream()
                        .filter(p -> p != winner)
                        .mapToLong(Player::getTotalInHand)
                        .max().orElse(0);

                long refund = winnerTotal - secondMaxTotal;
                if (refund > 0) {
                    winner.getChips().addAndGet(refund);
                    winner.setTotalInHand(winnerTotal - refund);
                    pot.addAndGet(-refund);
                }

                long winAmount = pot.get();
                winner.getChips().addAndGet(winAmount);

                lastShowdownPayouts.add(new ShowdownPayoutDTO(
                        winner.getUserId(),
                        winAmount,
                        "NO_SHOWDOWN",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        false,
                        false
                ));

                pot.set(0);

                if (eventListener != null) {
                    List<String> allPlayersIds = players.stream()
                            .filter(p -> p.hasCards())
                            .map(Player::getUserId).toList();

                    Map<String, Long> winnersMap = new HashMap<>();
                    winnersMap.put(winner.getUserId(), winAmount);

                    eventListener.onHandFinished(allPlayersIds, winnersMap);
                }
            }

            if (eventListener != null) {
                eventListener.onTableUpdate(this);
            }

            scheduleNextHand(PREMATURE_END_DELAY);
        }
    }

    private List<Player> determineWinners(List<Player> candidates) {
        synchronized (lock) {
            List<Card> board = getCommunityCards();
            Map<Player, HandResult> playerResults = new HashMap<>();
            for (Player player : candidates) {
                HandResult handResult = HandEvaluator.evaluate(player.getHand(), board);
                playerResults.put(player, handResult);
            }

            HandResult best = Collections.max(playerResults.values(), HandResult::compareTo);

            return playerResults.entrySet().stream()
                    .filter(entry -> entry.getValue().compareTo(best) == 0)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
    }
    private int distributePot() {
        lastShowdownPayouts.clear();
        Map<Player, Long> contributions = new HashMap<>();
        for (Player p : players) {
            if (p.getTotalInHand() > 0) {
                contributions.put(p, p.getTotalInHand());
            }
        }

        int potLayerIndex = 0;
        while (!contributions.isEmpty()) {
            List<Player> eligibleCandidates = contributions.keySet().stream()
                    .filter(Player::isInHand).toList();
            if (eligibleCandidates.isEmpty()) {
                break;
            }

            long minContribution = eligibleCandidates.stream()
                    .map(contributions::get).min(Long::compareTo).orElse(0L);

            long currentLayerTotal = 0;
            Iterator<Map.Entry<Player, Long>> iterator = contributions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Player, Long> entry = iterator.next();
                long taken = Math.min(entry.getValue(), minContribution);
                currentLayerTotal += taken;
                if (entry.getValue() - taken == 0) {
                    iterator.remove();
                } else {
                    entry.setValue(entry.getValue() - taken);
                }
            }

            List<Player> winners = determineWinners(eligibleCandidates);
            List<Player> losers = eligibleCandidates.stream()
                    .filter(p -> !winners.contains(p)).toList();

            long share = currentLayerTotal / winners.size();
            long remainder = currentLayerTotal % winners.size();

            List<Card> board = getCommunityCards();
            for (int i = 0; i < winners.size(); i++) {
                Player w = winners.get(i);
                long winAmount = (i == 0) ? share + remainder : share;
                w.getChips().addAndGet(winAmount);

                HandResult winRes = HandEvaluator.evaluate(w.getHand(), board);

                boolean isKickerWinner = false;
                boolean needKickersInJson = false;

                if (!losers.isEmpty()) {
                    HandResult bestLoserRes = losers.stream()
                            .map(l -> HandEvaluator.evaluate(l.getHand(), board))
                            .max(HandResult::compareTo).get();

                    if (winRes.getCategory() == bestLoserRes.getCategory()) {
                        int mainRanksCount = (winRes.getCategory() == HandCategory.TWO_PAIRS ||
                                winRes.getCategory() == HandCategory.FULL_HOUSE) ? 2 : 1;

                        boolean mainRanksAreEqual = true;
                        for (int k = 0; k < mainRanksCount; k++) {
                            if (winRes.getTieBreakers().get(k).compareTo(bestLoserRes.getTieBreakers().get(k)) != 0) {
                                mainRanksAreEqual = false;
                                break;
                            }
                        }

                        if (mainRanksAreEqual) {
                            isKickerWinner = true;
                            needKickersInJson = true;
                        }
                    }
                }

                if (winRes.getKickerCards().isEmpty()) {
                    needKickersInJson = false;
                    isKickerWinner = false;
                }

                lastShowdownPayouts.add(new ShowdownPayoutDTO(
                        w.getUserId(),
                        winAmount,
                        HandEvaluator.resolveHandName(w.getHand(), board, winRes),
                        winRes.getRankCards().stream().map(c -> c.getShortName().toUpperCase()).toList(),
                        needKickersInJson ? winRes.getKickerCards().stream().map(c -> c.getShortName().toUpperCase()).toList() : Collections.emptyList(),
                        potLayerIndex > 0,
                        isKickerWinner
                ));
            }
            potLayerIndex++;
        }

        if (eventListener != null) {
            List<String> allPlayersIds = players.stream()
                    .filter(p -> p.hasCards())
                    .map(Player::getUserId).toList();

            Map<String, Long> winnersMap = new HashMap<>();
            for (ShowdownPayoutDTO payout : lastShowdownPayouts) {
                winnersMap.merge(payout.userId(), payout.amount(), Long::sum);
            }
            eventListener.onHandFinished(allPlayersIds, winnersMap);
        }

        return potLayerIndex;
    }

    public void joinTable(Player player) {
        synchronized (lock) {
            if (players.stream().anyMatch(p -> p.getUserId().equals(player.getUserId()))) {
                throw new PlayerAlreadyJoinedException("error.player.already.joined");
            }
            if (players.size() >= MAX_PLAYERS) {
                throw new TableFullException("Table is full");
            }
            long buyIn = player.getChips().get();
            if (buyIn < minBuyIn) {
                throw new ChipAmountException("error.chips.min.buyin", minBuyIn);
            } else if (buyIn > maxBuyIn) {
                throw new ChipAmountException("error.chips.max.buyin", maxBuyIn);
            }

            player.setStatus(PlayerStatus.WAITING);
            players.add(player);

            if (eventListener != null) {
                eventListener.onPlayerJoin(this.id, player);
                eventListener.onTableUpdate(this);
            }

            long playersWithMoney = players.stream()
                    .filter(p -> p.getChips().get() >= bigBlindBet)
                    .count();

            if (playersWithMoney >= MIN_PLAYERS && state == TableStates.WAITING_FOR_PLAYERS && !isTransitioning) {
                this.isTransitioning = true;
                final long epoch = beginHandScheduleEpoch();
                pendingStartTask = scheduler.schedule(() -> {
                    synchronized (lock) {
                        if (epoch != handScheduleEpoch) {
                            return;
                        }
                        long checkAgain = players.stream()
                                .filter(p -> p.getChips().get() >= bigBlindBet)
                                .count();
                        if (checkAgain >= MIN_PLAYERS && state == TableStates.WAITING_FOR_PLAYERS) {
                            startNewHand();
                        } else if (state == TableStates.WAITING_FOR_PLAYERS) {
                            this.isTransitioning = false;
                        }
                    }
                }, START_GAME_DELAY, TimeUnit.SECONDS);
            }
        }
    }
    public void leaveTable(Player player) {
        synchronized (lock) {
            if (!players.contains(player)) {
                return;
            }

            boolean wasActivePlayer = false;
            int seatIndex = player.getSeatIndex();

            if (player.isInHand()) {
                if (isPlayerTurn(player)) {
                    stopTimer();
                    wasActivePlayer = true;
                }
                processFold(player);
            }

            long finalChipsToReturn = player.getChips().get();

            if (eventListener != null) {
                eventListener.onPlayerLeave(this.id, player.getUserId(), finalChipsToReturn, seatIndex);
            }

            players.remove(player);

            if (players.isEmpty()) {
                stopTimer();
                beginHandScheduleEpoch();
                this.isTransitioning = false;
                cleanupTable();
                this.dealerIdx = -1;
                return;
            }

            if (state == TableStates.WAITING_FOR_PLAYERS || state == TableStates.CLEANUP) {
                long playersWithMoney = players.stream()
                        .filter(p -> p.getChips().get() >= bigBlindBet)
                        .count();
                if (playersWithMoney < MIN_PLAYERS) {
                    cancelFuture(pendingStartTask);
                    pendingStartTask = null;
                    boolean nextHandPending =
                            (pendingNextHandCleanupTask != null && !pendingNextHandCleanupTask.isDone())
                                    || (pendingNextHandStartTask != null && !pendingNextHandStartTask.isDone());
                    if (!nextHandPending && state == TableStates.WAITING_FOR_PLAYERS) {
                        this.isTransitioning = false;
                    }
                }
                if (eventListener != null) {
                    eventListener.onTableUpdate(this);
                }
                return;
            }

            long playersInHand = players.stream()
                    .filter(p -> p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.WAITING)
                    .count();

            if (playersInHand < 2) {
                finishHandPrematurely();
                return;
            }

            if (wasActivePlayer) {
                if (isTransitioning) {
                    if (eventListener != null) {
                        eventListener.onTableUpdate(this);
                    }
                    return;
                }
                boolean hasNext = advanceTurn();
                if (!hasNext) {
                    if (eventListener != null) {
                        eventListener.onTableUpdate(this);
                    }
                    endBettingRound();
                } else {
                    startTimer();
                    if (eventListener != null) {
                        eventListener.onTableUpdate(this);
                    }
                }
            } else {
                if (eventListener != null && state != TableStates.WAITING_FOR_PLAYERS) {
                    eventListener.onTableUpdate(this);
                }
            }
        }
    }

    public long getTurnTimeoutMs() {
        return TURN_TIMEOUT * 1000L;
    }
    private void stopTimer() {
        if (currentTimer != null && !currentTimer.isDone()) {
            currentTimer.cancel(false);
        }
    }
    private void startTimer() {
        stopTimer();

        long activeCount = players.stream().filter(p -> p.getStatus() == PlayerStatus.ACTIVE).count();
        if (activeCount < 1
                || state == TableStates.WAITING_FOR_PLAYERS
                || state == TableStates.SHOWDOWN
                || state == TableStates.CLEANUP) {
            return;
        }

        this.isTransitioning = false;
        this.turnStartTime = System.currentTimeMillis();

        final int expectedSeatIdx = this.activePlayerIdx;

        currentTimer = scheduler.schedule(() -> {
            synchronized (lock) {
                if (state == TableStates.WAITING_FOR_PLAYERS
                        || state == TableStates.SHOWDOWN
                        || state == TableStates.CLEANUP) {
                    return;
                }

                if (this.activePlayerIdx != expectedSeatIdx) return;

                Player timedOutPlayer = getPlayerBySeat(activePlayerIdx);
                if (timedOutPlayer != null) {
                    timedOutPlayer.incrementMissedTurns();

                    if (timedOutPlayer.isKickRequired()) {
                        leaveTable(timedOutPlayer);
                    } else {
                        processFold(timedOutPlayer);

                        if (eventListener != null) {
                            eventListener.onPlayerAction(this.id, timedOutPlayer, ActionType.FOLD, 0, pot.get());
                        }

                        long survivors = players.stream().filter(Player::isInHand).count();
                        if (survivors < 2) {
                            finishHandPrematurely();
                            return;
                        }

                        boolean hasNext = advanceTurn();
                        if (!hasNext) {
                            if (eventListener != null) eventListener.onTableUpdate(this);
                            endBettingRound();
                        } else {
                            startTimer();
                            if (eventListener != null) {
                                eventListener.onTableUpdate(this);
                            }
                        }
                    }
                }
            }
        }, TURN_TIMEOUT, TimeUnit.SECONDS);
    }
    public long getTurnStartTime() {
        return turnStartTime;
    }
    private void cleanupTable() {
        synchronized (lock) {
            clearCommunityCards();
            this.pot.set(0);
            this.currentMaxBet = 0;
            this.activePlayerIdx = -1;
            this.state = TableStates.WAITING_FOR_PLAYERS;
            this.lastShowdownPayouts.clear();

            for (Player p : players) {
                p.clearHand();
                p.setTotalInHand(0);
                p.setRoundContribution(0);

                long totalMoney = p.getWalletBalance().get() + p.getChips().get();

                if (totalMoney < bigBlindBet) {
                    if (p.getStatus() != PlayerStatus.SITTING_OUT) {
                        p.setStatus(PlayerStatus.SITTING_OUT);

                        final long kickDeadline = System.currentTimeMillis() + 5000L;
                        p.setSitOutDeadline(kickDeadline);

                        scheduler.schedule(() -> {
                            synchronized (lock) {
                                if (players.contains(p) && p.getStatus() == PlayerStatus.SITTING_OUT
                                        && p.getSitOutDeadline() == kickDeadline) {
                                    try {
                                        leaveTable(p);
                                        if (eventListener != null) {
                                            eventListener.onTableUpdate(this);
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                        }, KICK_OUT_OF_MONEY_DELAY, TimeUnit.SECONDS);
                    }
                } else if (p.getChips().get() < bigBlindBet) {
                    if (p.getStatus() != PlayerStatus.SITTING_OUT) {
                        p.setStatus(PlayerStatus.SITTING_OUT);

                        final long rebuyDeadline = System.currentTimeMillis() + (REBUY_TIMEOUT * 1000L);
                        p.setSitOutDeadline(rebuyDeadline);

                        scheduler.schedule(() -> {
                            synchronized (lock) {
                                if (players.contains(p) && p.getStatus() == PlayerStatus.SITTING_OUT
                                        && p.getSitOutDeadline() == rebuyDeadline) {
                                    try {
                                        leaveTable(p);
                                        if (eventListener != null) eventListener.onTableUpdate(this);
                                    } catch (Exception ignored) {}
                                }
                            }
                        }, REBUY_TIMEOUT, TimeUnit.SECONDS);
                    }
                } else {
                    p.setStatus(PlayerStatus.WAITING);
                    p.setSitOutDeadline(0L);
                }
            }

            this.deck = new Deck();
        }
    }

    public void handleAction(Player player, PlayerAction action) {
        synchronized (lock) {
            if (isTransitioning) throw new IllegalTableStateException("error.table.transitioning");
            if (!isPlayerTurn(player)) throw new NotYourTurnException("error.not.your.turn");

            switch (action.type()) {
                case FOLD -> processFold(player);
                case CALL -> processCall(player);
                case RAISE -> processRaise(player, action.amount());
                case CHECK -> processCheck(player);
                case ALL_IN -> processAllIn(player);
            }

            stopTimer();
            player.resetMissedTurns();

            if (eventListener != null) {
                eventListener.onPlayerAction(this.id, player, action.type(), action.amount(), pot.get());
            }

            long survivors = players.stream().filter(Player::isInHand).count();
            if (survivors < 2) {
                finishHandPrematurely();
                return;
            }

            boolean hasNextPlayer = advanceTurn();

            if (!hasNextPlayer) {
                endBettingRound();
            } else {
                startTimer();
                if (eventListener != null) {
                    eventListener.onTableUpdate(this);
                }
            }
        }
    }

    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public boolean isPrivate() {
        return isPrivate;
    }
    public String getPasscode() {
        return passcode;
    }
    public int getFreeSeat() {
        Set<Integer> occupiedSeats = players.stream().map(Player::getSeatIndex).collect(Collectors.toSet());
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!occupiedSeats.contains(i)) {
                return i;
            }
        }
        throw new TableFullException("error.table.full");
    }
    public int getNextActivePlayerSeat(int currentSeat) {
        List<Integer> activeSeats = players.stream()
                .filter(p -> p.getStatus() == PlayerStatus.ACTIVE)
                .map(Player::getSeatIndex)
                .sorted()
                .toList();

        if (activeSeats.isEmpty()) return -1;

        for (Integer seat : activeSeats) {
            if (seat > currentSeat) return seat;
        }
        return activeSeats.get(0);
    }
    public int getMIN_PLAYERS() {
        return MIN_PLAYERS;
    }
    public int getMAX_PLAYERS() {
        return MAX_PLAYERS;
    }
    public long getSmallBlindBet() {
        return smallBlindBet;
    }
    public long getBigBlindBet() {
        return bigBlindBet;
    }
    public long getMinBuyIn() {
        return minBuyIn;
    }
    public long getMaxBuyIn() {
        return maxBuyIn;
    }
    public long getCurrentMaxBet() {
        return currentMaxBet;
    }
    public TableStates getState() {
        return state;
    }
    public int getPlayerCount() {
        return players.size();
    }
    public long getPot() {
        return pot.get();
    }
    public int getDealerIdx() {
        return dealerIdx;
    }
    public int getActivePlayerIdx() {
        return activePlayerIdx;
    }
    public int getMaxPlayers() {
        return MAX_PLAYERS;
    }
    public List<Player> getPlayers() {
        synchronized(lock) {
            return List.copyOf(players);
        }
    }
    public Optional<Player> findPlayerById(String userId) {
        return players.stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst();
    }
    public List<ShowdownPayoutDTO> getLastShowdownPayouts() {
        return lastShowdownPayouts;
    }

    public void bufferEvent(Object event) {
        long now = System.currentTimeMillis();
        recentEvents.offer(new BufferedEvent(now, event));
        recentEvents.removeIf(e -> (now - e.timestamp()) > EVENT_TTL_MS);
    }
    public java.util.List<Object> getEventsSince(long timestamp) {
        return recentEvents.stream()
                .filter(e -> e.timestamp() > timestamp)
                .map(BufferedEvent::eventPayload)
                .toList();
    }

    public record BufferedEvent(long timestamp, Object eventPayload) {}
}
