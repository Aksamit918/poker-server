package com.poker.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EmoteCatalog {

    public record EmoteDefinition(
            String emoteId,
            String name,
            String emoji,
            long price,
            boolean isDefault,
            boolean active
    ) {
    }

    private static final long PAID_EMOTE_PRICE = 200_000L;
    private static final long LOTTIE_PRICE_5K = 5_000L;
    private static final long LOTTIE_PRICE_10K = 10_000L;
    private static final long LOTTIE_PRICE_20K = 20_000L;
    private static final long CROWN_EMOTE_PRICE = 10_000L;
    private static final long DIAMOND_EMOTE_PRICE = 10_000L;
    private static final long PIGGY_EMOTE_PRICE = 1_000_000L;
    private static final long SAD_EMOTE_PRICE = 5_000L;

    private static final Map<String, EmoteDefinition> CATALOG = new LinkedHashMap<>();

    static {
        addDefault("fire", "Огонь", "🔥");
        addDefault("cry", "Слёзы", "😭");
        addDefault("angry", "Злость", "🤬");
        addDefault("poop", "Какашка", "💩");
        addDefault("beer", "Пивко", "🍻");
        addDefault("clown", "Клоун", "🤡");

        addPaid("royal_crown", "Королевская корона", "👑", CROWN_EMOTE_PRICE);
        addPaid("diamond_hand", "Алмаз", "💎", DIAMOND_EMOTE_PRICE);
        addPaid("goat_king", "GOAT", "🐐", PAID_EMOTE_PRICE);
        addPaid("piggy", "Копилка", "🐷", PIGGY_EMOTE_PRICE);
        addPaid("sad_emoji", "Грусть", "😢", SAD_EMOTE_PRICE);
        addPaid("vino", "Винцо", "🍷", LOTTIE_PRICE_20K);
        addPaid("cool", "Крутость", "😎", LOTTIE_PRICE_20K);
        addPaid("hourglass", "Время", "⏳", LOTTIE_PRICE_5K);
        addPaid("like", "Like", "👍", LOTTIE_PRICE_5K);
        addPaid("flex", "Флекс", "💪", LOTTIE_PRICE_10K);
        addPaid("raised_eyebrow", "Поднятые брови", "🤨", LOTTIE_PRICE_20K);
        addPaid("thinking", "Задумчивость", "🤔", LOTTIE_PRICE_10K);
    }

    private EmoteCatalog() {
    }

    private static void addDefault(String emoteId, String name, String emoji) {
        CATALOG.put(emoteId, new EmoteDefinition(emoteId, name, emoji, 0L, true, true));
    }

    private static void addPaid(String emoteId, String name, String emoji, long price) {
        CATALOG.put(emoteId, new EmoteDefinition(emoteId, name, emoji, price, false, true));
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
