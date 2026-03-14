package com.jellyfintozidoo;

import com.google.gson.JsonObject;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for playback reporting JSON body builders and watched threshold logic.
 */
public class PlaybackReportingTest {

    // buildPlaybackStartBody tests

    @Test
    public void buildPlaybackStartBody_containsAllFields() {
        JsonObject body = JellyfinApi.buildPlaybackStartBody("item123", "session456");

        assertEquals("item123", body.get("ItemId").getAsString());
        assertEquals("session456", body.get("PlaySessionId").getAsString());
        assertTrue(body.get("CanSeek").getAsBoolean());
        assertEquals("DirectPlay", body.get("PlayMethod").getAsString());
        assertEquals(0L, body.get("PositionTicks").getAsLong());
    }

    // buildPlaybackProgressBody tests

    @Test
    public void buildPlaybackProgressBody_whilePlaying_containsAllFields() {
        JsonObject body = JellyfinApi.buildPlaybackProgressBody("item123", "session456", 50000000L, false);

        assertEquals("item123", body.get("ItemId").getAsString());
        assertEquals("session456", body.get("PlaySessionId").getAsString());
        assertEquals(50000000L, body.get("PositionTicks").getAsLong());
        assertFalse(body.get("IsPaused").getAsBoolean());
        assertTrue(body.get("CanSeek").getAsBoolean());
    }

    @Test
    public void buildPlaybackProgressBody_whilePaused_isPausedTrue() {
        JsonObject body = JellyfinApi.buildPlaybackProgressBody("item123", "session456", 50000000L, true);

        assertTrue(body.get("IsPaused").getAsBoolean());
    }

    // buildPlaybackStoppedBody tests

    @Test
    public void buildPlaybackStoppedBody_containsAllFields() {
        JsonObject body = JellyfinApi.buildPlaybackStoppedBody("item123", "session456", 72000000000L);

        assertEquals("item123", body.get("ItemId").getAsString());
        assertEquals("session456", body.get("PlaySessionId").getAsString());
        assertEquals(72000000000L, body.get("PositionTicks").getAsLong());
    }

    // isWatched tests

    @Test
    public void isWatched_above90Percent_returnsTrue() {
        // 91% of 10,000,000,000 ticks = 9,100,000,000
        assertTrue(JellyfinApi.isWatched(9100000000L, 10000000000L));
    }

    @Test
    public void isWatched_below90Percent_returnsFalse() {
        // 89% of 10,000,000,000 ticks = 8,900,000,000
        assertFalse(JellyfinApi.isWatched(8900000000L, 10000000000L));
    }

    @Test
    public void isWatched_zeroDuration_returnsFalse() {
        assertFalse(JellyfinApi.isWatched(5000000000L, 0L));
    }
}
