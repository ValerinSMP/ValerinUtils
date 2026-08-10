package me.mtynnn.valerinutils.core;

public final class EarningsChange {
    private EarningsChange() {
    }

    public static EarningsCurrency currency(String currencyId, double oldAmount, double newAmount) {
        return positiveDelta(oldAmount, newAmount) > 0 ? EarningsCurrency.fromId(currencyId) : null;
    }

    public static EarningsCurrency currency(boolean cancelled, String currencyId, double oldAmount, double newAmount) {
        return cancelled ? null : currency(currencyId, oldAmount, newAmount);
    }

    public static double positiveDelta(double oldAmount, double newAmount) {
        double delta = newAmount - oldAmount;
        return Double.isFinite(delta) && delta > 0 ? delta : 0;
    }
}
