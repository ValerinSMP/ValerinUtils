package me.mtynnn.valerinutils.integrations.excellenteconomy;

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
}
