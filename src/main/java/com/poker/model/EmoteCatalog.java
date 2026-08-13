package com.poker.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EmoteCatalog {

    public record EmoteDefinition(String emoteId, long price, boolean isDefault, boolean active) {
    }

    private static final long PAID_EMOTE_PRICE = 200_000L;
    private static final long PIGGY_EMOTE_PRICE = 1_000_000L;

    private static final Map<String, EmoteDefinition> CATALOG = new LinkedHashMap<>();

    static {
        addDefault("fire");
        addDefault("cry");
        addDefault("angry");
        addDefault("poop");
        addDefault("beer");
        addDefault("clown");

        addPaid("royal_crown");
        addPaid("diamond_hand");
        addPaid("goat_king");
        addPaid("piggy", PIGGY_EMOTE_PRICE);
    }

    private EmoteCatalog() {
    }

    private static void addDefault(String emoteId) {
        CATALOG.put(emoteId, new EmoteDefinition(emoteId, 0L, true, true));
    }

    private static void addPaid(String emoteId) {
        addPaid(emoteId, PAID_EMOTE_PRICE);
    }

    private static void addPaid(String emoteId, long price) {
        CATALOG.put(emoteId, new EmoteDefinition(emoteId, price, false, true));
    }

    public static Collection<EmoteDefinition> all() {
        return List.copyOf(CATALOG.values());
    }

    public static List<String> defaultEmoteIds() {
        return CATALOG.values().stream()
                .filter(EmoteDefinition::isDefault)
                .map(EmoteDefinition::emoteId)
                .toList();
    }

    public static List<String> paidEmoteIds() {
        return CATALOG.values().stream()
                .filter(def -> def.active() && !def.isDefault())
                .map(EmoteDefinition::emoteId)
                .toList();
    }

    public static Optional<EmoteDefinition> find(String emoteId) {
        if (emoteId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CATALOG.get(emoteId));
    }

    public static boolean exists(String emoteId) {
        return find(emoteId).isPresent();
    }
}
