package me.mtynnn.valerinutils.core;

import java.util.Locale;

public enum EarningsCurrency {
    MONEY("money", "total_money_earned"),
    SHARDS("shards", "total_shards_earned");

    private final String id;
    private final String column;

    EarningsCurrency(String id, String column) {
        this.id = id;
        this.column = column;
    }

    public String id() {
        return id;
    }

    String column() {
        return column;
    }

    public static EarningsCurrency fromId(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "money" -> MONEY;
            case "shards" -> SHARDS;
            default -> null;
        };
    }
}
