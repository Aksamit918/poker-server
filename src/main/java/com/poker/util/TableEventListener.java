package com.poker.util;

import com.poker.model.Table;

public interface TableEventListener {
    void onTableUpdate(Table table);

    void onPlayerLeave(String userId, long chips);
}