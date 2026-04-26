package com.poker.model;

import com.poker.exception.TableFullException;
import com.poker.exception.*;
import com.poker.util.HandEvaluator;
import com.poker.util.PlayerLeaveListener;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Table {
    private final Object lock = new Object();
    private final PlayerLeaveListener leaveListener;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> currentTimer;
    private static final int TURN_TIMEOUT = 10;
    private final String id;
    private String name;
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
    public Table(String id, String name, long smallBlindBet, long bigBlindBet, int MIN_PLAYERS, int MAX_PLAYERS,
                 PlayerLeaveListener leaveListener) {
        this.id = id;
        this.name = name;
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
        this.leaveListener = leaveListener;
    }
    private void setupPositions() {
        if (this.dealerIdx == -1) {
            this.dealerIdx = players.get(new Random().nextInt(players.size())).getSeatIndex();
        } else {
            this.dealerIdx = getNextPlayerSeat(dealerIdx);
        }

        this.smallBlindIdx = getNextPlayerSeat(dealerIdx);
        this.bigBlindIdx = getNextPlayerSeat(smallBlindIdx);

        this.activePlayerIdx = getNextPlayerSeat(bigBlindIdx);
    }
    private void startNewHand() {
        for (Player p : players) {
            p.setTotalInHand(0);
            p.setRoundContribution(0);
            if (p.isEligibleForNewHand()) {
                p.setStatus(PlayerStatus.ACTIVE);
            }
        }

        long activePlayers = players.stream()
                .filter(p -> p.getStatus() == PlayerStatus.ACTIVE)
                .count();
        if (activePlayers < MIN_PLAYERS) {
            cleanupTable();
            return;
        }

        setupPositions();
        long smallBlindForcedBet = getPlayerBySeat(smallBlindIdx).bet(smallBlindBet);
        long bigBlindForcedBet = getPlayerBySeat(bigBlindIdx).bet(bigBlindBet);
        pot.addAndGet(smallBlindForcedBet);
        pot.addAndGet(bigBlindForcedBet);
        getPlayerBySeat(smallBlindIdx).addToTotalInHand(smallBlindForcedBet);
        getPlayerBySeat(smallBlindIdx).addToRoundContribution(smallBlindForcedBet);
        getPlayerBySeat(bigBlindIdx).addToTotalInHand(bigBlindForcedBet);
        getPlayerBySeat(bigBlindIdx).addToRoundContribution(bigBlindForcedBet);
        this.currentMaxBet = bigBlindForcedBet;
        dealCards();
        this.state = TableStates.PRE_FLOP;
        startTimer();
    }
    private void scheduleNextHand() {
        scheduler.schedule(() -> {
            synchronized (lock) {
                if (players.size() >= MIN_PLAYERS && (state == TableStates.WAITING_FOR_PLAYERS ||
                        state == TableStates.SHOWDOWN)) {
                    cleanupTable();
                    startNewHand();
                }
            }
        }, 15, TimeUnit.SECONDS);
    }
    private void setTableState(TableStates state) {
        this.state = state;
    }
    private void dealCards() {
        synchronized (lock) {
            for (Player player : players) {
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
            nextIdx = getNextPlayerSeat(nextIdx);
            Player p = getPlayerBySeat(nextIdx);
            if (p.getStatus() == PlayerStatus.ACTIVE) {
                activePlayerIdx = nextIdx;
                return true;
            }
        }
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
        if (amountToCall <= 0) {
            throw new IllegalCallException("There is no bet to call. Please use CHECK instead.");
        }
        long actualPaid = player.bet(amountToCall);
        pot.addAndGet(actualPaid);
        player.addToRoundContribution(actualPaid);
        player.addToTotalInHand(actualPaid);
        updateStatusAfterBet(player);
    }
    private void processRaise(Player player, long newMaxBet) {
        if (newMaxBet <= currentMaxBet) {
            throw new IllegalRaiseException("New Max Bet must exceed Current Max Bet");
        }
        long amountToRaise = newMaxBet - player.getRoundContribution();
        long actualPaid = player.bet(amountToRaise);
        pot.addAndGet(actualPaid);
        player.addToRoundContribution(actualPaid);
        player.addToTotalInHand(actualPaid);
        updateStatusAfterBet(player);
        this.currentMaxBet = newMaxBet;
        players.stream()
                .filter(p -> p != player)
                .filter(Player::canAct)
                .forEach(p -> p.setStatus(PlayerStatus.ACTIVE));
    }
    private void processCheck(Player player) {
        if (player.getRoundContribution() < currentMaxBet) {
            throw new IllegalCheckException("Round Contribution must be equal to current Max Bet");
        }
        player.setStatus(PlayerStatus.CHECKED);
    }
    private void processAllIn(Player player) {
        long chips = player.getChips().get();
        long currentContribution = player.getRoundContribution();

        long maxOpponentCanCommit = players.stream()
                .filter(p -> p != player && p.canAct())
                .mapToLong(p -> p.getChips().get() + p.getRoundContribution())
                .max()
                .orElse(0);

        long effectiveTotalBet = Math.min(currentContribution + chips, Math.max(currentMaxBet, maxOpponentCanCommit));

        long amountToBet = effectiveTotalBet - currentContribution;

        long actualPaid = player.bet(amountToBet);
        pot.addAndGet(actualPaid);
        player.addToRoundContribution(actualPaid);
        player.addToTotalInHand(actualPaid);

        if (player.getChips().get() == 0) {
            player.setStatus(PlayerStatus.ALL_IN);
        } else {
            player.setStatus(PlayerStatus.RAISED);
        }

        if (player.getRoundContribution() > currentMaxBet) {
            this.currentMaxBet = player.getRoundContribution();

            for (Player p : players) {
                if (p != player && p.getStatus() != PlayerStatus.FOLDED
                        && p.getStatus() != PlayerStatus.ALL_IN && p.getStatus() != PlayerStatus.WAITING) {
                    p.setStatus(PlayerStatus.ACTIVE);
                }
            }
        }
    }
    public void rebuy(Player player, long amount, long walletBalance) {
        synchronized (lock) {
            if (state != TableStates.WAITING_FOR_PLAYERS) {
                throw new IllegalTableStateException("Rebuy is not allowed while a hand is in progress");
            }

            long currentChips = player.getChips().get();
            if (currentChips + amount > maxBuyIn) {
                throw new ChipAmountException("Rebuy amount exceeds the maximum table limit: " + maxBuyIn);
            }

            long increasedChips = currentChips + amount;
            if (increasedChips < minBuyIn) {
                throw new ChipAmountException("Total stack after rebuy must meet the minimum requirement of " + minBuyIn);
            }

            player.getWalletBalance().set(walletBalance);

            player.getChips().addAndGet(amount);

            if (player.getStatus() == PlayerStatus.SITTING_OUT) {
                player.setStatus(PlayerStatus.WAITING);
            }

            if (players.size() >= MIN_PLAYERS && state == TableStates.WAITING_FOR_PLAYERS) {
                startNewHand();
            }
        }
    }

    private void endBettingRound() {
        List<Player> survivors = players.stream()
                .filter(Player::isInHand)
                .toList();

        if (survivors.size() == 1 && state != TableStates.WAITING_FOR_PLAYERS) {
            this.isTransitioning = true;
            Player winner = survivors.get(0);
            winner.getChips().addAndGet(pot.get());
            pot.set(0);
            this.isTransitioning = true;
            cleanupTable();
            scheduleNextHand();
            return;
        }

        stopTimer();

        this.isTransitioning = true;

        scheduler.schedule(() -> {
            synchronized (lock) {
                List<Player> survivorsCheck = players.stream()
                        .filter(Player::isInHand)
                        .toList();

                if (survivorsCheck.size() < 2 || state == TableStates.WAITING_FOR_PLAYERS) {
                    return;
                }

                switch (this.state) {
                    case PRE_FLOP -> {
                        setTableState(TableStates.FLOP);
                        dealFlop();
                    }
                    case FLOP ->  {
                        setTableState(TableStates.TURN);
                        dealTurn();
                    }
                    case TURN ->  {
                        setTableState(TableStates.RIVER);
                        dealRiver();
                    }
                    case RIVER ->  {
                        setTableState(TableStates.SHOWDOWN);
                        distributePot();
                        pot.set(0);
                    }
                }

                if (this.state != TableStates.SHOWDOWN) {
                    this.currentMaxBet = 0;

                    long playersWhoCanBet = players.stream()
                            .filter(p -> p.getStatus() != PlayerStatus.FOLDED &&
                                    p.getStatus() != PlayerStatus.ALL_IN &&
                                    p.getStatus() != PlayerStatus.WAITING)
                            .count();

                    if (playersWhoCanBet < 2) {
                        endBettingRound();
                    } else {
                        for (Player p : players) {
                            p.setRoundContribution(0);
                            if (p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.ALL_IN && p.getStatus() != PlayerStatus.WAITING) {
                                p.setStatus(PlayerStatus.ACTIVE);
                            }
                        }
                        this.activePlayerIdx = dealerIdx;
                        advanceTurn();
                        startTimer();
                    }

                } else {
                    scheduleNextHand();
                }
            }
        }, 5, TimeUnit.SECONDS);
    }
    private void finishHandPrematurely() {
        stopTimer();

        List<Player> winners = players.stream()
                .filter(Player::isInHand)
                .toList();

        if (!winners.isEmpty()) {
            winners.get(0).getChips().addAndGet(pot.get());
            pot.set(0);
        }

        cleanupTable();
        scheduleNextHand();
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
    private void distributePot() {
        Map<Player, Long> contributions = new HashMap<>();
        for (Player p : players) {
            if (p.getTotalInHand() > 0) {
                contributions.put(p, p.getTotalInHand());
            }
        }

        while (!contributions.isEmpty()) {

            List<Player> eligibleCandidates = contributions.keySet().stream()
                    .filter(Player::isInHand)
                    .toList();

            if (eligibleCandidates.isEmpty()) {
                break;
            }

            long minContribution = eligibleCandidates.stream()
                    .map(contributions::get)
                    .min(Long::compareTo)
                    .orElse(0L);

            if (minContribution == 0) break;

            int currentPotLayer = 0;
            Iterator<Map.Entry<Player, Long>> iterator = contributions.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Player, Long> entry = iterator.next();
                long playerContributed = entry.getValue();

                long taken = Math.min(playerContributed, minContribution);

                currentPotLayer += taken;
                long newValue = playerContributed - taken;

                if (newValue == 0) {
                    iterator.remove();
                } else {
                    entry.setValue(newValue);
                }
            }

            List<Player> winners = determineWinners(eligibleCandidates);

            int share = currentPotLayer / winners.size();
            int remainder = currentPotLayer % winners.size();

            for (int i = 0; i < winners.size(); i++) {
                if (i == 0) {
                    winners.get(i).getChips().addAndGet(share + remainder);
                } else {
                    winners.get(i).getChips().addAndGet(share);
                }
            }
        }
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

            if (players.size() >= MIN_PLAYERS && state == TableStates.WAITING_FOR_PLAYERS) {
                startNewHand();
            }
        }
    }
    public void leaveTable(Player player) {
        synchronized (lock) {
            if (!players.contains(player)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found");
            }

            int removedIdx = players.indexOf(player);

            if (player.isInHand()) {
                if (isPlayerTurn(player)) {
                    stopTimer();
                }
                processFold(player);
            }

            if (leaveListener != null) {
                leaveListener.onPlayerLeave(player.getUserId(), player.getChips().get());
            }

            players.remove(player);

            if (removedIdx <= dealerIdx) {
                dealerIdx--;
            }

            if (players.isEmpty()) {
                cleanupTable();
                this.dealerIdx = -1;
                return;
            }

            activePlayerIdx = (activePlayerIdx - 1 + players.size()) % players.size();

            long playersInHand = players.stream()
                    .filter(p -> p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.WAITING)
                    .count();

            if (playersInHand < 2 && state != TableStates.WAITING_FOR_PLAYERS) {
                finishHandPrematurely();
                return;
            }

            boolean hasNext = advanceTurn();
            if (!hasNext) {
                endBettingRound();
            } else if (state != TableStates.WAITING_FOR_PLAYERS) {
                startTimer();
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
                    if (timedOutPlayer.isKickRequired()) {
                        leaveTable(timedOutPlayer);
                    }
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
                }
            }
        }, TURN_TIMEOUT, TimeUnit.MINUTES);
    }
    private void cleanupTable() {
        communityCards.clear();
        for (Player p : players) {
            p.clearHand();
            p.setTotalInHand(0);
            p.setRoundContribution(0);

            if (p.getChips().get() < minBuyIn) {
                if (p.getStatus() != PlayerStatus.SITTING_OUT) {
                    p.setStatus(PlayerStatus.SITTING_OUT);

                    scheduler.schedule(() -> {
                        synchronized (lock) {
                            if (players.contains(p) && p.getStatus() == PlayerStatus.SITTING_OUT) {
                                try {
                                    leaveTable(p);
                                } catch (Exception e) {

                                }
                            }
                        }
                    }, 30, TimeUnit.SECONDS);
                }
            } else {
                p.setStatus(PlayerStatus.WAITING);
            }
        }
        this.deck = new Deck();
        this.state = TableStates.WAITING_FOR_PLAYERS;
        this.activePlayerIdx = -1;
        this.currentMaxBet = 0;
    }

    public void handleAction(Player player, PlayerAction action) {
        synchronized (lock) {
            if (isTransitioning) {
                throw new IllegalTableStateException("Please wait, cards are being dealt.");
            }

            if (!isPlayerTurn(player)) {
                throw new NotYourTurnException("It is not your turn!");
            }

            stopTimer();

            switch (action.type()) {
                case FOLD -> processFold(player);
                case CALL -> processCall(player);
                case RAISE -> processRaise(player, action.amount());
                case CHECK -> processCheck(player);
                case ALL_IN -> processAllIn(player);
            }
            player.resetMissedTurns();

            long survivors = players.stream().filter(Player::isInHand).count();

            if (survivors < 2) {
                finishHandPrematurely();
                return;
            }

            boolean hasNextPlayer = advanceTurn();

            if (!hasNextPlayer) {
                endBettingRound();
            } else {
                if (state != TableStates.WAITING_FOR_PLAYERS && state != TableStates.SHOWDOWN) {
                    startTimer();
                } else {
                    stopTimer();
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
    public int getFreeSeat() {
        Set<Integer> occupiedSeats = players.stream().map(Player::getSeatIndex).collect(Collectors.toSet());
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!occupiedSeats.contains(i)) {
                return i;
            }
        }
        throw new TableFullException("Table is full");
    }
    public int getNextPlayerSeat(int currentSeat) {
        Set<Integer> occupiedSeats = players.stream().map(Player::getSeatIndex).collect(Collectors.toSet());
        if (occupiedSeats.isEmpty()) return -1;
        int nextSeat = currentSeat;
        for (int i = 0; i < MAX_PLAYERS; i++) {
            nextSeat = (currentSeat + 1) % MAX_PLAYERS;
            if (occupiedSeats.contains(nextSeat)) {
                break;
            }
            currentSeat = nextSeat;
        }
        return nextSeat;
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
}
