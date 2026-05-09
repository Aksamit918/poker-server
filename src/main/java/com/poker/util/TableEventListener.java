package com.poker.util;

import com.poker.model.ActionType;
import com.poker.model.Player;
import com.poker.model.Table;

public interface TableEventListener {
    void onTableUpdate(Table table);
    void onPlayerLeave(String userId, long chips);
    void onPlayerJoin(String tableId, Player player);
    void onPlayerAction(String tableId, Player player, ActionType type, long amount, long pot);
}