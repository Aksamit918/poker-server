package com.poker.model;

import com.poker.exception.TableFullException;
import com.poker.exception.*;
import com.poker.util.HandEvaluator;
import com.poker.util.PlayerLeaveListener;
import com.poker.util.TableEventListener;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
                    if (p.getChips().get() >= bigBlindBet) {
                        p.setStatus(PlayerStatus.ACTIVE);
                    } else {
                        p.setStatus(PlayerStatus.SITTING_OUT);
                    }
                }

                long activeCount = players.stream().filter(p -> p.getStatus() == PlayerStatus.ACTIVE).count();
                if (activeCount < MIN_PLAYERS) {
                    this.state = TableStates.WAITING_FOR_PLAYERS;
                    cleanupTable();
                    if (eventListener != null) eventListener.onTableUpdate(this);
                    return;
                }

                setupPositions();

                Player sbPlayer = getPlayerBySeat(smallBlindIdx);
                Player bbPlayer = getPlayerBySeat(bigBlindIdx);

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
            System.err.println("CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
            this.isTransitioning = false;
        }
    }
    private void scheduleNextHand(int delayInSeconds) {
        this.isTransitioning = true;
        scheduler.schedule(() -> {
            try {
                synchronized (lock) {
                    if (players.size() < MIN_PLAYERS) {
                        this.state = TableStates.WAITING_FOR_PLAYERS;
                        cleanupTable();
                        this.isTransitioning = false;
                        if (eventListener != null) eventListener.onTableUpdate(this);
                        return;
                    }
                    startNewHand();
                }
            } catch (Exception e) {
                System.err.println("!!! ERROR IN scheduleNextHand !!!");
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
            }

            if (players.size() >= MIN_PLAYERS && state == TableStates.WAITING_FOR_PLAYERS) {
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

            long canBet = players.stream()
                    .filter(p -> p.getStatus() != PlayerStatus.FOLDED &&
                            p.getStatus() != PlayerStatus.ALL_IN &&
                            p.getStatus() != PlayerStatus.WAITING)
                    .count();

            int delay = (canBet < 2) ? 1 : 2;

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

                        int showdownDelay = 0;
                        if (this.state == TableStates.SHOWDOWN) {
                            int layers = distributePot();
                            pot.set(0);
                            showdownDelay = (layers * 3) + 2;
                        }

                        if (eventListener != null) {
                            eventListener.onTableUpdate(this);
                        }

                        if (this.state == TableStates.SHOWDOWN) {
                            scheduleNextHand(showdownDelay);
                        } else {
                            this.currentMaxBet = 0;
                            for (Player p : players) {
                                p.setRoundContribution(0);
                                if (p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.ALL_IN && p.getStatus() != PlayerStatus.WAITING) {
                                    p.setStatus(PlayerStatus.ACTIVE);
                                }
                            }

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
                                if (eventListener != null) eventListener.onTableUpdate(this);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("FATAL ERROR IN endBettingRound: " + e.getMessage());
                    e.printStackTrace();
                    this.isTransitioning = false;
                }
            }, delay, TimeUnit.SECONDS);
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

            cleanupTable();

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
        Map<Player, Long> contributions = new HashMap<>();
        for (Player p : players) {
            if (p.getTotalInHand() > 0) {
                contributions.put(p, p.getTotalInHand());
            }
        }

        int potLayers = 0;

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

            if (winners.isEmpty()) {
                System.err.println("CRITICAL ERROR: No winners found in distributePot!");
                break;
            }

            int share = currentPotLayer / winners.size();
            int remainder = currentPotLayer % winners.size();

            for (int i = 0; i < winners.size(); i++) {
                if (i == 0) {
                    winners.get(i).getChips().addAndGet(share + remainder);
                } else {
                    winners.get(i).getChips().addAndGet(share);
                }
            }

            potLayers++;
        }

        return potLayers;
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

            if (players.size() >= MIN_PLAYERS && state == TableStates.WAITING_FOR_PLAYERS && !isTransitioning) {
                this.isTransitioning = true;
                scheduler.schedule(() -> {
                    synchronized (lock) {
                        if (players.size() >= MIN_PLAYERS && state == TableStates.WAITING_FOR_PLAYERS) {
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

            int removedIdx = players.indexOf(player);

            if (player.isInHand()) {
                if (isPlayerTurn(player)) {
                    stopTimer();
                }
                processFold(player);
            }

            players.remove(player);

            if (eventListener != null) {
                eventListener.onPlayerLeave(player.getUserId(), player.getChips().get());
                eventListener.onTableUpdate(this);
            }

            if (removedIdx <= dealerIdx) {
                dealerIdx--;
            }

            if (players.isEmpty()) {
                cleanupTable();
                this.dealerIdx = -1;
                return;
            }

            if (state == TableStates.WAITING_FOR_PLAYERS) {
                return;
            }

            activePlayerIdx = (activePlayerIdx - 1 + players.size()) % players.size();

            long playersInHand = players.stream()
                    .filter(p -> p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.WAITING)
                    .count();

            if (playersInHand < 2) {
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

        for (Player p : players) {
            p.clearHand();
            p.setTotalInHand(0);
            p.setRoundContribution(0);

            if (p.getChips().get() < bigBlindBet) {
                if (p.getStatus() != PlayerStatus.SITTING_OUT) {

                    long totalMoney = p.getWalletBalance().get() + p.getChips().get();

                    if (totalMoney < bigBlindBet) {
                        leaveTable(p);
                        continue;
                    }

                    p.setStatus(PlayerStatus.SITTING_OUT);

                    scheduler.schedule(() -> {
                        synchronized (lock) {
                            if (players.contains(p) && p.getStatus() == PlayerStatus.SITTING_OUT) {
                                try {
                                    System.out.println("DEBUG: Player " + p.getUserId() + " kicked due to rebuy timeout.");
                                    leaveTable(p);
                                } catch (Exception e) {
                                    System.err.println("Error kicking player: " + e.getMessage());
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
}
