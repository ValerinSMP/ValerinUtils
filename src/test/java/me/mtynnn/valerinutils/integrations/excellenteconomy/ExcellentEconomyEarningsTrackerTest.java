package me.mtynnn.valerinutils.integrations.excellenteconomy;

import me.mtynnn.valerinutils.core.EarningsChange;
import me.mtynnn.valerinutils.core.EarningsCurrency;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcellentEconomyEarningsTrackerTest {

    @Test
    void reloadCannotClaimASecondListenerRegistration() {
        ExcellentEconomyEarningsTracker tracker = new ExcellentEconomyEarningsTracker(null);

        assertTrue(tracker.claimStart());
        assertFalse(tracker.claimStart());
    }

    @Test
    void asynchronousChangeSchedulesExactlyOneMainThreadWrite() {
        List<Runnable> scheduled = new ArrayList<>();
        AtomicInteger writes = new AtomicInteger();

        ExcellentEconomyEarningsTracker.dispatch(true, writes::incrementAndGet, scheduled::add);

        assertEquals(0, writes.get());
        assertEquals(1, scheduled.size());
        scheduled.getFirst().run();
        assertEquals(1, writes.get());
    }

    @Test
    void normalPositiveChangesRemainTrackableWhilePayFrameIsExcluded() {
        String currencyManager = "su.nightexpress.excellenteconomy.currency.CurrencyManager";

        assertEquals(EarningsCurrency.MONEY, EarningsChange.currency("money", 10, 15));
        assertEquals(EarningsCurrency.SHARDS, EarningsChange.currency("shards", 2, 4));
        assertTrue(ExcellentEconomyEarningsTracker.isTransferFrame(currencyManager, "send"));
        assertFalse(ExcellentEconomyEarningsTracker.isTransferFrame(currencyManager, "give"));
        assertFalse(ExcellentEconomyEarningsTracker.isTransferFrame(currencyManager, "sendPayment"));
        assertFalse(ExcellentEconomyEarningsTracker.isTransferFrame(currencyManager + "Proxy", "send"));
        assertFalse(ExcellentEconomyEarningsTracker.isTransferFrame("another.Plugin", "send"));
    }
}
