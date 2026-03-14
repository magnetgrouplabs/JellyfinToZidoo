package com.jellyfintozidoo;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for IntroSkipper response parsing.
 * Tests parseIntroSkipperResponse() and IntroSkipperResult conversion methods.
 */
public class IntroSkipperApiTest {

    @Test
    public void parseIntroSkipperResponse_bothValid_returnsAllTimestamps() {
        String json = "{"
                + "\"Introduction\": {"
                + "  \"EpisodeId\": \"abc123\","
                + "  \"Valid\": true,"
                + "  \"IntroStart\": 30.5,"
                + "  \"IntroEnd\": 90.25"
                + "},"
                + "\"Credits\": {"
                + "  \"EpisodeId\": \"abc123\","
                + "  \"Valid\": true,"
                + "  \"IntroStart\": 1313.264,"
                + "  \"IntroEnd\": 1408.264"
                + "}"
                + "}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(30500L, result.introStartMs());
        assertEquals(90250L, result.introEndMs());
        assertEquals(1313264L, result.creditStartMs());
        assertEquals(1408264L, result.creditEndMs());
    }

    @Test
    public void parseIntroSkipperResponse_onlyIntroduction_creditsAreNegativeOne() {
        String json = "{"
                + "\"Introduction\": {"
                + "  \"Valid\": true,"
                + "  \"IntroStart\": 10.0,"
                + "  \"IntroEnd\": 50.0"
                + "}"
                + "}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(10000L, result.introStartMs());
        assertEquals(50000L, result.introEndMs());
        assertEquals(-1L, result.creditStartMs());
        assertEquals(-1L, result.creditEndMs());
    }

    @Test
    public void parseIntroSkipperResponse_onlyCredits_introAreNegativeOne() {
        String json = "{"
                + "\"Credits\": {"
                + "  \"Valid\": true,"
                + "  \"IntroStart\": 1200.0,"
                + "  \"IntroEnd\": 1350.0"
                + "}"
                + "}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
        assertEquals(1200000L, result.creditStartMs());
        assertEquals(1350000L, result.creditEndMs());
    }

    @Test
    public void parseIntroSkipperResponse_introNotValid_introAreNegativeOne() {
        String json = "{"
                + "\"Introduction\": {"
                + "  \"Valid\": false,"
                + "  \"IntroStart\": 10.0,"
                + "  \"IntroEnd\": 50.0"
                + "},"
                + "\"Credits\": {"
                + "  \"Valid\": true,"
                + "  \"IntroStart\": 1200.0,"
                + "  \"IntroEnd\": 1350.0"
                + "}"
                + "}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
        assertEquals(1200000L, result.creditStartMs());
        assertEquals(1350000L, result.creditEndMs());
    }

    @Test
    public void parseIntroSkipperResponse_emptyJson_allNegativeOne() {
        String json = "{}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
        assertEquals(-1L, result.creditStartMs());
        assertEquals(-1L, result.creditEndMs());
    }

    @Test
    public void parseIntroSkipperResponse_convertSecondsToMs_correctly() {
        // 145.792 seconds = 145792 ms
        String json = "{"
                + "\"Introduction\": {"
                + "  \"Valid\": true,"
                + "  \"IntroStart\": 145.792,"
                + "  \"IntroEnd\": 228.497"
                + "}"
                + "}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(145792L, result.introStartMs());
        assertEquals(228497L, result.introEndMs());
    }

    @Test
    public void introSkipperResult_introStartMs_negativeOneWhenNotPresent() {
        String json = "{}";
        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
    }

    @Test
    public void introSkipperResult_creditEndMs_convertsCorrectly() {
        String json = "{"
                + "\"Credits\": {"
                + "  \"Valid\": true,"
                + "  \"IntroStart\": 1313.264,"
                + "  \"IntroEnd\": 1408.264"
                + "}"
                + "}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(1408264L, result.creditEndMs());
    }
}
