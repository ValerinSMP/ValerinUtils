package me.mtynnn.valerinutils.placeholders;

import me.mtynnn.valerinutils.core.EarningsChange;
import me.mtynnn.valerinutils.core.EarningsCurrency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EconomyPlaceholderTest {

    @Test
    void keepsPublicIdsAndWholeNumberFormat() {
        assertEquals("125", EconomyPlaceholder.resolve("earnings_money", currency -> 125.9));
        assertEquals("48", EconomyPlaceholder.resolve("earnings_shards", currency -> 48.7));
        assertEquals("73", EconomyPlaceholder.resolve("earnings_money", currency -> 73));
    }

    @Test
    void mapsExcellentEconomyIncreasesAndIgnoresOtherChanges() {
        assertEquals(EarningsCurrency.MONEY, EarningsChange.currency("money", 10, 14.5));
        assertEquals(EarningsCurrency.SHARDS, EarningsChange.currency("SHARDS", 3, 5));
        assertNull(EarningsChange.currency("money", 10, 9));
        assertNull(EarningsChange.currency("tokens", 1, 2));
        assertNull(EarningsChange.currency(true, "money", 10, 20));
    }

    @Test
    void calculatesOnlyThePositiveDelta() {
        assertEquals(4.5, EarningsChange.positiveDelta(10, 14.5));
        assertEquals(0, EarningsChange.positiveDelta(10, 10));
        assertEquals(0, EarningsChange.positiveDelta(10, 4));
    }
}
