package com.poker.util;

import com.poker.dto.events.StreetEndDTO;
import com.poker.model.ActionType;
import com.poker.model.Player;
import com.poker.model.Table;

import java.util.List;

public interface TableEventListener {
    void onTableUpdate(Table table);
    void onPlayerLeave(String userId, long chips, int seatIndex);
    void onPlayerJoin(String tableId, Player player);
    void onPlayerAction(String tableId, Player player, ActionType type, long amount, long pot);
    void onHandFinished(List<String> playersInHand, java.util.Map<String, Long> winnersAndAmounts);
    void onStreetEnd(StreetEndDTO event);
}