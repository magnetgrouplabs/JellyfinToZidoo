package com.jellyfintozidoo;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for JellyfinApi.ticksToMs() -- converting Jellyfin ticks to milliseconds.
 */
public class TickConversionTest {

    @Test
    public void ticksToMs_zero_returnsZero() {
        assertEquals(0, JellyfinApi.ticksToMs(0));
    }

    @Test
    public void ticksToMs_oneHour_returns3600000() {
        // 1 hour = 3,600,000 ms = 36,000,000,000 ticks
        assertEquals(3600000L, JellyfinApi.ticksToMs(36000000000L));
    }

    @Test
    public void ticksToMs_minimumUnit_returnsOne() {
        // 10,000 ticks = 1 ms
        assertEquals(1L, JellyfinApi.ticksToMs(10000L));
    }

    @Test
    public void ticksToMs_twoHours_returns7200000() {
        // 2 hours = 7,200,000 ms = 72,000,000,000 ticks (typical movie runtime)
        assertEquals(7200000L, JellyfinApi.ticksToMs(72000000000L));
    }

    @Test
    public void ticksToMs_subMillisecond_roundsDown() {
        // 5,000 ticks < 10,000 ticks per ms, integer division rounds down to 0
        assertEquals(0L, JellyfinApi.ticksToMs(5000L));
    }

    // msToTicks tests

    @Test
    public void msToTicks_zero_returnsZero() {
        assertEquals(0L, JellyfinApi.msToTicks(0));
    }

    @Test
    public void msToTicks_oneMs_returns10000() {
        assertEquals(10000L, JellyfinApi.msToTicks(1));
    }

    @Test
    public void msToTicks_oneHour_returns36Billion() {
        // 1 hour = 3,600,000 ms = 36,000,000,000 ticks
        assertEquals(36000000000L, JellyfinApi.msToTicks(3600000L));
    }

    @Test
    public void msToTicks_threeHours_verifyLongArithmetic() {
        // 3 hours = 10,800,000 ms = 108,000,000,000 ticks (exceeds int range)
        assertEquals(108000000000L, JellyfinApi.msToTicks(10800000L));
    }
}
