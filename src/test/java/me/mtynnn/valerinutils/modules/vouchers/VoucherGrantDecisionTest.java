package me.mtynnn.valerinutils.modules.vouchers;

import org.junit.jupiter.api.Test;

import static me.mtynnn.valerinutils.modules.vouchers.VoucherGrantDecision.State.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VoucherGrantDecisionTest {
    @Test
    void pendingGrantRecoveryIsIdempotent() {
        assertEquals(new VoucherGrantDecision.Result(PENDING, 5), VoucherGrantDecision.unavailable(5));
        assertEquals(PENDING, VoucherGrantDecision.recover(5, 0).state()); // crash before save
        assertEquals(PENDING, VoucherGrantDecision.afterAttempt(5, 5).state()); // full inventory
        assertEquals(new VoucherGrantDecision.Result(PARTIAL, 3),
                VoucherGrantDecision.afterAttempt(5, 3));
        assertEquals(DONE, VoucherGrantDecision.recover(5, 5).state()); // crash after save/restart
        assertEquals(new VoucherGrantDecision.Result(PARTIAL, 3),
                VoucherGrantDecision.recover(5, 2));
        assertEquals(DONE, VoucherGrantDecision.recover(5, 7).state()); // replay never redelivers
    }
}
