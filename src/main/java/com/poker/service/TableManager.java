package com.poker.service;

import com.poker.model.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TableManager {
    private final Map<String, Table> tables = new ConcurrentHashMap<>();
    public String createTable(int smallBlind, int bigBlind, int minPlayersNum, int maxPlayersNum) {
        String id = null;

        do {
            id = UUID.randomUUID().toString();
        } while (tables.containsKey(id));

        Table newTable = new Table(id, smallBlind, bigBlind, minPlayersNum, maxPlayersNum);
        tables.put(id, newTable);

        return id;
    }
    public Table getTable(String id) {
        return tables.get(id);
    }
    public Table removeTable(String id) {
        return tables.remove(id);
    }
    public List<Table> getAllTables() {
        return new ArrayList<>(tables.values());
    }
}
