package com.poker.util;

import com.poker.model.Player;
import com.poker.model.Table;

public interface TableEventListener {
    void onTableUpdate(Table table);
    void onPlayerLeave(String userId, long chips);
    void onPlayerJoin(String tableId, Player player);
}