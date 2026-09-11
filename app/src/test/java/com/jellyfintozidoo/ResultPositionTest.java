package com.jellyfintozidoo;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for Play.resolveResultPositionMs(), which decides the position handed back to the
 * launching client on the result intent.
 *
 * The official Jellyfin Android TV client sends its own playback stop when this activity returns,
 * using the "position" extra off the result intent. A missing value means played to completion on
 * the server, so a value is always returned: the real final position mid playback, and 0 whenever
 * the client must not write a resume point at all.
 */
public class ResultPositionTest {

    private static final String ITEM = "abc123";
    private static final String OTHER_ITEM = "def456";

    // 10 minutes = 600,000 ms; 100 minutes = 6,000,000 ms
    private static final long TEN_MINUTES_MS = 600000L;
    private static final long HUNDRED_MINUTES_TICKS = msToTicks(6000000L);

    private static long msToTicks(long ms) {
        return ms * 10000L;
    }

    @Test
    public void midStopOnTheSameItemReturnsThePosition() {
        assertEquals(TEN_MINUTES_MS, Play.resolveResultPositionMs(ITEM, ITEM,
                TEN_MINUTES_MS, msToTicks(TEN_MINUTES_MS), HUNDRED_MINUTES_TICKS, 0L));
    }

    @Test
    public void watchedItemReturnsZero() {
        // 95 minutes of a 100 minute item is past the 90 percent threshold.
        long positionMs = 5700000L;
        assertEquals(0, Play.resolveResultPositionMs(ITEM, ITEM,
                positionMs, msToTicks(positionMs), HUNDRED_MINUTES_TICKS, 0L));
    }

    @Test
    public void itemChangedReturnsZeroEvenWithAPosition() {
        assertEquals(0, Play.resolveResultPositionMs(ITEM, OTHER_ITEM,
                TEN_MINUTES_MS, msToTicks(TEN_MINUTES_MS), HUNDRED_MINUTES_TICKS, TEN_MINUTES_MS));
    }

    @Test
    public void nullCurrentItemReturnsZero() {
        assertEquals(0, Play.resolveResultPositionMs(ITEM, null,
                TEN_MINUTES_MS, msToTicks(TEN_MINUTES_MS), HUNDRED_MINUTES_TICKS, TEN_MINUTES_MS));
    }

    @Test
    public void unknownPositionReturnsTheFallback() {
        assertEquals(TEN_MINUTES_MS, Play.resolveResultPositionMs(ITEM, ITEM,
                0L, 0L, HUNDRED_MINUTES_TICKS, TEN_MINUTES_MS));
    }

    @Test
    public void unknownPositionWithNoFallbackReturnsZero() {
        assertEquals(0, Play.resolveResultPositionMs(ITEM, ITEM,
                0L, 0L, HUNDRED_MINUTES_TICKS, 0L));
    }

    @Test
    public void zeroDurationIsNeverWatchedSoThePositionSurvives() {
        // JellyfinApi.isWatched returns false when the duration is 0, so an unknown duration
        // leaves the real position in place instead of wiping it to 0.
        assertFalse(JellyfinApi.isWatched(msToTicks(TEN_MINUTES_MS), 0L));
        assertEquals(TEN_MINUTES_MS, Play.resolveResultPositionMs(ITEM, ITEM,
                TEN_MINUTES_MS, msToTicks(TEN_MINUTES_MS), 0L, 0L));
    }
}
