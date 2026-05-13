package com.poker.model;

import com.poker.dto.ShowdownPayoutDTO;
import com.poker.exception.TableFullException;
import com.poker.exception.*;
import com.poker.util.TableEventListener;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Table {
    private final Object lock = new Object();
    private final TableEventListener eventListener;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> currentTimer;
    private static final int TURN_TIMEOUT = 15;
    private static final int STAGE_TRANSITION_DELAY = 2;
    private static final int REBUY_TIMEOUT = 30;
    private static final int SHOWDOWN_BASE_DELAY = 2;
    private static final int SHOWDOWN_LAYER_DELAY = 5;
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
    private final List<Card> communityCards;
    private AtomicLong pot;
    private long currentMaxBet = 0;
    private final long smallBlindBet;
    private final long bigBlindBet;
    private List<ShowdownPayoutDTO> lastShowdownPayouts = new ArrayList<>();

    public Table(String id, String name, long smallBlindBet, long bigBlindBet, int MIN_PLAYERS, int MAX_PLAYERS,
                 boolean isPrivate, String passcode, TableEventListener eventListener) {
        this.id = id;
        this.name = name;
        this.isPrivate = isPrivate;
        this.passcode = passcode;
        this.MIN_PLAYERS = MIN_PLAYERS;
        this.MAX_PLAYERS = MAX_PLAYERS;
        this.players = new CopyOnWriteArrayList<>();
        this.deck = new Deck();
        this.communityCards = new CopyOnWriteArrayList<>();
        this.pot = new AtomicLong(0);
        this.state = TableStates.WAITING_FOR_PLAYERS;
        this.smallBlindBet = smallBlindBet;
        this.bigBlindBet = bigBlindBet;
        this.minBuyIn = bigBlindBet * 10;
        this.maxBuyIn = bigBlindBet * 100;
        this.dealerIdx = -1;
        this.activePlayerIdx = -1;
        this.eventListener = eventListener;
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
    private void startNewHand() {
        try {
            synchronized (lock) {
                this.isTransitioning = false;

                for (Player p : players) {
                    if (p.getStatus() == PlayerStatus.WAITING && p.getChips().get() >= bigBlindBet) {
                        p.setStatus(PlayerStatus.ACTIVE);
                    }
                }

                long activeCount = players.stream().filter(p -> p.getStatus() == PlayerStatus.ACTIVE).count();

                if (activeCount < 2) {
                    for (Player p : players) {
                        if (p.getStatus() == PlayerStatus.ACTIVE) {
                            p.setStatus(PlayerStatus.WAITING);
                        }
                    }
                    this.state = TableStates.WAITING_FOR_PLAYERS;
                    if (eventListener != null) {
                        eventListener.onTableUpdate(this);
                    }
                    return;
                }

                setupPositions();

                Player sbPlayer = getPlayerBySeat(smallBlindIdx);
                Player bbPlayer = getPlayerBySeat(bigBlindIdx);

                if (sbPlayer == null || bbPlayer == null || sbPlayer.getStatus() != PlayerStatus.ACTIVE || bbPlayer.getStatus() != PlayerStatus.ACTIVE) {
                    setupPositions();
                    sbPlayer = getPlayerBySeat(smallBlindIdx);
                    bbPlayer = getPlayerBySeat(bigBlindIdx);
                }

                long sbPaid = sbPlayer.bet(smallBlindBet);
                long bbPaid = bbPlayer.bet(bigBlindBet);

                pot.set(sbPaid + bbPaid);
                sbPlayer.addToTotalInHand(sbPaid);
                sbPlayer.addToRoundContribution(sbPaid);
                bbPlayer.addToTotalInHand(bbPaid);
                bbPlayer.addToRoundContribution(bbPaid);

                this.currentMaxBet = bigBlindBet;
                this.deck = new Deck();
                dealCards();

                this.state = TableStates.PRE_FLOP;

                if (eventListener != null) eventListener.onTableUpdate(this);
                startTimer();
            }
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR IN startNewHand: " + e.getMessage());
            e.printStackTrace();
            this.isTransitioning = false;
        }
    }
    private void scheduleNextHand(int delayInSeconds) {
        this.isTransitioning = true;

        scheduler.schedule(() -> {
            try {
                synchronized (lock) {
                    cleanupTable();

                    long readyPlayers = players.stream()
                            .filter(p -> p.getStatus() == PlayerStatus.WAITING)
                            .count();

                    if (readyPlayers < MIN_PLAYERS) {
                        this.isTransitioning = false;
                        this.state = TableStates.WAITING_FOR_PLAYERS;
                        if (eventListener != null) {
                            eventListener.onTableUpdate(this);
                        }
                        System.out.println("DEBUG: Not enough players to start. Table is now waiting.");
                        return;
                    }

                    startNewHand();
                }
            } catch (Exception e) {
                System.err.println("CRITICAL ERROR IN scheduleNextHand: " + e.getMessage());
                e.printStackTrace();
                this.isTransitioning = false;
            }
        }, delayInSeconds, TimeUnit.SECONDS);
    }
    private void setTableState(TableStates state) {
        this.state = state;
    }
    private void dealCards() {
        for (Player player : players) {
            if (player.getStatus() == PlayerStatus.ACTIVE) {
                player.addCard(deck.drawCard());
                player.addCard(deck.drawCard());
            }
        }
    }
    private void dealFlop() {
        synchronized (lock) {
            communityCards.add(deck.drawCard());
            communityCards.add(deck.drawCard());
            communityCards.add(deck.drawCard());
        }
    }
    private void dealTurn() {
        synchronized (lock) {
            communityCards.add(deck.drawCard());
        }
    }
    private void dealRiver() {
        synchronized (lock) {
            communityCards.add(deck.drawCard());
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
    private void processRaise(Player player, long newMaxBet) {
        if (newMaxBet <= currentMaxBet) throw new IllegalRaiseException("error.illegal.raise", currentMaxBet);

        long amountToRaise = newMaxBet - player.getRoundContribution();
        long actualPaid = player.bet(amountToRaise);
        pot.addAndGet(actualPaid);
        player.addToRoundContribution(actualPaid);
        player.addToTotalInHand(actualPaid);
        updateStatusAfterBet(player);
        this.currentMaxBet = newMaxBet;

        for (Player p : players) {
            if (p != player &&
                    p.getStatus() != PlayerStatus.FOLDED &&
                    p.getStatus() != PlayerStatus.ALL_IN &&
                    p.getStatus() != PlayerStatus.WAITING) {
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
        long chips = player.getChips().get();
        pot.addAndGet(chips);
        player.getChips().set(0);
        player.addToRoundContribution(chips);
        player.addToTotalInHand(chips);
        player.setStatus(PlayerStatus.ALL_IN);

        if (player.getRoundContribution() > currentMaxBet) {
            this.currentMaxBet = player.getRoundContribution();
            for (Player p : players) {
                if (p != player && p.getStatus() != PlayerStatus.FOLDED &&
                        p.getStatus() != PlayerStatus.ALL_IN && p.getStatus() != PlayerStatus.WAITING) {
                    p.setStatus(PlayerStatus.ACTIVE);
                }
            }
        }
    }
    public void rebuy(Player player, long amount, long walletBalance) {
        synchronized (lock) {
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
            if (isTransitioning) return;
            this.isTransitioning = true;
            stopTimer();

            scheduler.schedule(() -> {
                try {
                    synchronized (lock) {
                        this.isTransitioning = false;

                        switch (this.state) {
                            case PRE_FLOP -> { setTableState(TableStates.FLOP); dealFlop(); }
                            case FLOP -> { setTableState(TableStates.TURN); dealTurn(); }
                            case TURN -> { setTableState(TableStates.RIVER); dealRiver(); }
                            case RIVER -> setTableState(TableStates.SHOWDOWN);
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
                            if (eventListener != null) {
                                eventListener.onTableUpdate(this);
                            }

                            long stillCanBet = players.stream()
                                    .filter(p -> p.getStatus() != PlayerStatus.FOLDED &&
                                            p.getStatus() != PlayerStatus.ALL_IN &&
                                            p.getStatus() != PlayerStatus.WAITING)
                                    .count();

                            if (stillCanBet < 2) {
                                endBettingRound();
                            } else {
                                this.currentMaxBet = 0;
                                for (Player p : players) {
                                    p.setRoundContribution(0);
                                    if (p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.ALL_IN && p.getStatus() != PlayerStatus.WAITING) {
                                        p.setStatus(PlayerStatus.ACTIVE);
                                    }
                                }
                                this.activePlayerIdx = dealerIdx;
                                advanceTurn();
                                startTimer();
                                if (eventListener != null) eventListener.onTableUpdate(this);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("FATAL ERROR IN endBettingRound: " + e.getMessage());
                    e.printStackTrace();
                    this.isTransitioning = false;
                }
            }, STAGE_TRANSITION_DELAY, TimeUnit.SECONDS);
        }
    }
    private void finishHandPrematurely() {
        synchronized (lock) {
            if (this.state == TableStates.CLEANUP) return;

            stopTimer();
            this.isTransitioning = true;
            this.state = TableStates.CLEANUP;

            List<Player> winners = players.stream()
                    .filter(Player::isInHand)
                    .toList();

            if (!winners.isEmpty()) {
                winners.get(0).getChips().addAndGet(pot.get());
                pot.set(0);
            }

            if (eventListener != null) {
                eventListener.onTableUpdate(this);
            }

            scheduleNextHand(4);
        }
    }

    private List<Player> determineWinners(List<Player> candidates) {
        synchronized (lock) {
            Map<Player, HandResult> playerResults = new HashMap<>();
            for (Player player : candidates) {
                HandResult handResult = HandEvaluator.evaluate(player.getHand(), communityCards);
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
            if (p.getTotalInHand() > 0) contributions.put(p, p.getTotalInHand());
        }

        int potLayerIndex = 0;
        while (!contributions.isEmpty()) {
            List<Player> eligibleCandidates = contributions.keySet().stream()
                    .filter(Player::isInHand).toList();
            if (eligibleCandidates.isEmpty()) break;

            long minContribution = eligibleCandidates.stream()
                    .map(contributions::get).min(Long::compareTo).orElse(0L);

            long currentLayerTotal = 0;
            Iterator<Map.Entry<Player, Long>> iterator = contributions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Player, Long> entry = iterator.next();
                long taken = Math.min(entry.getValue(), minContribution);
                currentLayerTotal += taken;
                if (entry.getValue() - taken == 0) iterator.remove();
                else entry.setValue(entry.getValue() - taken);
            }

            List<Player> winners = determineWinners(eligibleCandidates);
            List<Player> losers = eligibleCandidates.stream()
                    .filter(p -> !winners.contains(p)).toList();

            long share = currentLayerTotal / winners.size();
            long remainder = currentLayerTotal % winners.size();

            for (int i = 0; i < winners.size(); i++) {
                Player w = winners.get(i);
                long winAmount = (i == 0) ? share + remainder : share;
                w.getChips().addAndGet(winAmount);

                HandResult winRes = HandEvaluator.evaluate(w.getHand(), communityCards);

                boolean isKickerWinner = false;
                boolean needKickersInJson = false;

                if (!losers.isEmpty()) {
                    HandResult bestLoserRes = losers.stream()
                            .map(l -> HandEvaluator.evaluate(l.getHand(), communityCards))
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
                        winRes.getCategory().name(),
                        winRes.getRankCards().stream().map(Card::getShortName).toList(),
                        needKickersInJson ? winRes.getKickerCards().stream().map(Card::getShortName).toList() : Collections.emptyList(),
                        potLayerIndex > 0,
                        isKickerWinner
                ));
            }
            potLayerIndex++;
        }
        return potLayerIndex;
    }

    public void joinTable(Player player) {
        synchronized (lock) {
            if (players.stream().anyMatch(p -> p.getUserId().equals(player.getUserId()))) {
                throw new PlayerAlreadyJoinedException("User already joined");
            }
            if (players.size() >= MAX_PLAYERS) {
                throw new TableFullException("Table is full");
            }
            long buyIn = player.getChips().get();
            if (buyIn < minBuyIn) {
                throw new ChipAmountException("Insufficient buy-in. Minimum required: " + minBuyIn);
            } else if (buyIn > maxBuyIn) {
                throw new ChipAmountException("Buy-in exceeds limit. Maximum allowed: " + maxBuyIn);
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
                scheduler.schedule(() -> {
                    synchronized (lock) {
                        long checkAgain = players.stream()
                                .filter(p -> p.getChips().get() >= bigBlindBet)
                                .count();
                        if (checkAgain >= MIN_PLAYERS && state == TableStates.WAITING_FOR_PLAYERS) {
                            startNewHand();
                        } else {
                            this.isTransitioning = false;
                        }
                    }
                }, 3, TimeUnit.SECONDS);
            }
        }
    }
    public void leaveTable(Player player) {
        synchronized (lock) {
            if (!players.contains(player)) {
                throw new PlayerNotFoundException("error.player.not.found");
            }

            boolean wasActivePlayer = false;

            if (player.isInHand()) {
                if (isPlayerTurn(player)) {
                    stopTimer();
                    wasActivePlayer = true;
                }
                processFold(player);
            }

            players.remove(player);

            if (eventListener != null) {
                eventListener.onPlayerLeave(player.getUserId(), player.getChips().get());
                // eventListener.onTableUpdate(this);
            }

            if (players.isEmpty()) {
                cleanupTable();
                this.dealerIdx = -1;
                return;
            }

            if (state == TableStates.WAITING_FOR_PLAYERS) {
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
                boolean hasNext = advanceTurn();
                if (!hasNext) {
                    endBettingRound();
                } else {
                    startTimer();
                }
            }
        }
    }

    private void stopTimer() {
        if (currentTimer != null && !currentTimer.isDone()) {
            currentTimer.cancel(true);
        }
    }
    private void startTimer() {
        stopTimer();
        this.isTransitioning = false;

        long activeCount = players.stream().filter(p -> p.getStatus() == PlayerStatus.ACTIVE).count();
        if (activeCount < 1 || state == TableStates.WAITING_FOR_PLAYERS || state == TableStates.SHOWDOWN) {
            return;
        }

        currentTimer = scheduler.schedule(() -> {
            synchronized (lock) {
                if (state == TableStates.WAITING_FOR_PLAYERS || state == TableStates.SHOWDOWN) return;

                Player timedOutPlayer = getPlayerBySeat(activePlayerIdx);
                if (timedOutPlayer != null) {
                    timedOutPlayer.incrementMissedTurns();
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
                        endBettingRound();
                    } else {
                        startTimer();
                        if (eventListener != null) {
                            eventListener.onTableUpdate(this);
                        }
                    }

                    if (timedOutPlayer.isKickRequired()) {
                        leaveTable(timedOutPlayer);
                    }
                }
            }
        }, TURN_TIMEOUT, TimeUnit.SECONDS);
    }
    private void cleanupTable() {
        this.communityCards.clear();
        this.pot.set(0);
        this.currentMaxBet = 0;
        this.activePlayerIdx = -1;
        this.state = TableStates.WAITING_FOR_PLAYERS;
        this.lastShowdownPayouts.clear();

        List<Player> toRemove = new ArrayList<>();
        for (Player p : players) {
            p.clearHand();
            p.setTotalInHand(0);
            p.setRoundContribution(0);

            if (p.getChips().get() < bigBlindBet) {
                long totalMoney = p.getWalletBalance().get() + p.getChips().get();
                if (totalMoney < bigBlindBet) {
                    toRemove.add(p);
                    continue;
                }

                if (p.getStatus() != PlayerStatus.SITTING_OUT) {
                    p.setStatus(PlayerStatus.SITTING_OUT);
                    final long expectedDeadline = System.currentTimeMillis() + (REBUY_TIMEOUT * 1000L);
                    p.setSitOutDeadline(expectedDeadline);

                    scheduler.schedule(() -> {
                        synchronized (lock) {
                            if (players.contains(p) && p.getStatus() == PlayerStatus.SITTING_OUT
                                    && p.getSitOutDeadline() == expectedDeadline) {
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

        for (Player p : toRemove) {
            leaveTable(p);
        }

        this.deck = new Deck();
    }

    public void handleAction(Player player, PlayerAction action) {
        synchronized (lock) {
            if (isTransitioning) throw new IllegalTableStateException("error.table.transitioning");
            if (!isPlayerTurn(player)) throw new NotYourTurnException("error.not.your.turn");

            stopTimer();

            switch (action.type()) {
                case FOLD -> processFold(player);
                case CALL -> processCall(player);
                case RAISE -> processRaise(player, action.amount());
                case CHECK -> processCheck(player);
                case ALL_IN -> processAllIn(player);
            }
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
    public int getPrevPlayerSeat(int currentSeat) {
        Set<Integer> occupiedSeats = players.stream().map(Player::getSeatIndex).collect(Collectors.toSet());
        if (occupiedSeats.isEmpty()) return -1;
        int prevSeat = currentSeat;
        for (int i = 0; i < MAX_PLAYERS; i++) {
            prevSeat = (currentSeat - 1 + MAX_PLAYERS) % MAX_PLAYERS;
            if (occupiedSeats.contains(prevSeat)) {
                break;
            }
            currentSeat = prevSeat;
        }
        return prevSeat;
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
    public List<Card> getCommunityCards() {
        synchronized (lock) {
            return List.copyOf(communityCards);
        }
    }
    public List<Player> getPlayers() {
        synchronized(lock) {
            return List.copyOf(players); // Return a safe snapshot
        }
    }
    public Optional<Player> findPlayerById(String userId) {
        synchronized (lock) {
            return players.stream()
                    .filter(p -> p.getUserId().equals(userId))
                    .findFirst();
        }
    }
    public List<ShowdownPayoutDTO> getLastShowdownPayouts() {
        return lastShowdownPayouts;
    }
}
