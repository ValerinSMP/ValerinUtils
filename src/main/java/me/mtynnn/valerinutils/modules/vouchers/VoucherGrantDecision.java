package me.mtynnn.valerinutils.modules.vouchers;

final class VoucherGrantDecision {
    enum State { PENDING, DONE, PARTIAL }
    record Result(State state, int leftover) { }

    static Result unavailable(int requested) { return new Result(State.PENDING, requested); }

    static Result recover(int requested, int tagged) {
        if (tagged <= 0) return new Result(State.PENDING, requested);
        if (tagged >= requested) return new Result(State.DONE, 0);
        return new Result(State.PARTIAL, requested - tagged);
    }

    static Result afterAttempt(int requested, int leftover) {
        if (leftover >= requested) return new Result(State.PENDING, requested);
        if (leftover <= 0) return new Result(State.DONE, 0);
        return new Result(State.PARTIAL, leftover);
    }

    private VoucherGrantDecision() { }
}
