package com.poker.util;

import java.text.NumberFormat;
import java.util.Locale;

public class FormatUtils {
    private static final NumberFormat COMPACT_FORMAT = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);

    public static String format(long number) {
        return COMPACT_FORMAT.format(number);
    }

    public static String formatBlinds(long smallBlind, long bigBlind) {
        return format(smallBlind) + "/" + format(bigBlind);
    }
}