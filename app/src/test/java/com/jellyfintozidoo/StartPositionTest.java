package com.jellyfintozidoo;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for Play.resolveStartPositionMs(), which decides where playback starts.
 *
 * The Jellyfin Android client always puts a "position" extra on the external player intent
 * (ExternalPlayer.kt: putExtra("position", source.startTime.inWholeMilliseconds.toInt())) and
 * sets it to 0 when the user picks "Play from beginning", so a present extra always wins over
 * the item's saved server position, an explicit 0 included.
 */
public class StartPositionTest {

    // 10 minutes = 600,000 ms = 6,000,000,000 ticks
    private static final long TEN_MINUTES_TICKS = 6000000000L;
    private static final int TEN_MINUTES_MS = 600000;

    @Test
    public void playFromBeginning_explicitZeroBeatsSavedServerPosition() {
        assertEquals(0, Play.resolveStartPositionMs(true, 0, TEN_MINUTES_TICKS));
    }

    @Test
    public void resume_clientPositionWins() {
        assertEquals(TEN_MINUTES_MS, Play.resolveStartPositionMs(true, TEN_MINUTES_MS, 1234567890L));
    }

    @Test
    public void noExtra_fallsBackToSavedServerPosition() {
        assertEquals(TEN_MINUTES_MS, Play.resolveStartPositionMs(false, 0, TEN_MINUTES_TICKS));
    }

    @Test
    public void noExtra_andNoSavedPosition_startsAtZero() {
        assertEquals(0, Play.resolveStartPositionMs(false, 0, 0L));
    }

    @Test
    public void noExtra_ignoresStaleIntentValue() {
        // When the extra is absent the intent value is meaningless and must not be used.
        assertEquals(0, Play.resolveStartPositionMs(false, 999999, 0L));
    }

    @Test
    public void negativeClientPositionIsClampedToZero() {
        assertEquals(0, Play.resolveStartPositionMs(true, -1, TEN_MINUTES_TICKS));
    }

    @Test
    public void negativeSavedServerPositionIsIgnored() {
        assertEquals(0, Play.resolveStartPositionMs(false, 0, -5000L));
    }
}
