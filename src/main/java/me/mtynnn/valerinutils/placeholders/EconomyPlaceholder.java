package me.mtynnn.valerinutils.placeholders;

import me.mtynnn.valerinutils.core.EarningsCurrency;

import java.util.function.ToDoubleFunction;

final class EconomyPlaceholder {
    private EconomyPlaceholder() {
    }

    static String resolve(String params, ToDoubleFunction<EarningsCurrency> totals) {
        EarningsCurrency currency = switch (params) {
            case "earnings_money" -> EarningsCurrency.MONEY;
            case "earnings_shards" -> EarningsCurrency.SHARDS;
            default -> null;
        };
        return currency == null ? null : Long.toString((long) totals.applyAsDouble(currency));
    }
}
