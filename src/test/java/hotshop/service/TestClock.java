package hotshop.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Deterministic clock that only moves when a test advances it. */
final class TestClock extends Clock {
    private Instant now;

    TestClock(Instant start) {
        now = start;
    }

    void advanceSeconds(long seconds) {
        now = now.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
