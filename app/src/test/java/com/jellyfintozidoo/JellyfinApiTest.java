package com.jellyfintozidoo;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for JellyfinApi auth header construction and JSON response parsing.
 */
public class JellyfinApiTest {

    @Test
    public void buildAuthHeader_formatsCorrectly() {
        String header = JellyfinApi.buildAuthHeader("testkey123");
        assertEquals("MediaBrowser Token=\"testkey123\"", header);
    }

    @Test
    public void buildAuthHeader_withSpecialChars() {
        String header = JellyfinApi.buildAuthHeader("abc-123_XYZ");
        assertEquals("MediaBrowser Token=\"abc-123_XYZ\"", header);
    }

    @Test
    public void parseItemResponse_fullJson_extractsAllFields() throws Exception {
        String json = "{"
                + "\"Name\": \"Big Buck Bunny\","
                + "\"Path\": \"/media/movies/Big Buck Bunny/Big Buck Bunny.mkv\","
                + "\"UserData\": {"
                + "  \"PlaybackPositionTicks\": 36000000000"
                + "},"
                + "\"MediaSources\": [{"
                + "  \"Path\": \"/media/movies/Big Buck Bunny/Big Buck Bunny.mkv\""
                + "}]"
                + "}";

        JellyfinApi.ItemResult result = JellyfinApi.parseItemResponse(json);
        assertEquals("/media/movies/Big Buck Bunny/Big Buck Bunny.mkv", result.path);
        assertEquals(36000000000L, result.positionTicks);
        assertEquals("Big Buck Bunny", result.title);
    }

    @Test
    public void parseItemResponse_missingUserData_returnsZeroPosition() throws Exception {
        String json = "{"
                + "\"Name\": \"Test Movie\","
                + "\"Path\": \"/media/movies/test.mkv\""
                + "}";

        JellyfinApi.ItemResult result = JellyfinApi.parseItemResponse(json);
        assertEquals("/media/movies/test.mkv", result.path);
        assertEquals(0L, result.positionTicks);
        assertEquals("Test Movie", result.title);
    }

    @Test
    public void parseItemResponse_nullRootPath_fallsBackToMediaSources() throws Exception {
        String json = "{"
                + "\"Name\": \"Fallback Test\","
                + "\"MediaSources\": [{"
                + "  \"Path\": \"/media/movies/fallback.mkv\""
                + "}]"
                + "}";

        JellyfinApi.ItemResult result = JellyfinApi.parseItemResponse(json);
        assertEquals("/media/movies/fallback.mkv", result.path);
        assertEquals("Fallback Test", result.title);
    }

    @Test
    public void parseItemResponse_emptyRootPath_fallsBackToMediaSources() throws Exception {
        String json = "{"
                + "\"Name\": \"Empty Path Test\","
                + "\"Path\": \"\","
                + "\"MediaSources\": [{"
                + "  \"Path\": \"/media/movies/actual.mkv\""
                + "}]"
                + "}";

        JellyfinApi.ItemResult result = JellyfinApi.parseItemResponse(json);
        assertEquals("/media/movies/actual.mkv", result.path);
    }

    @Test
    public void parseItemResponse_noPathAnywhere_returnsEmptyString() throws Exception {
        String json = "{"
                + "\"Name\": \"No Path\""
                + "}";

        JellyfinApi.ItemResult result = JellyfinApi.parseItemResponse(json);
        assertEquals("", result.path);
    }

    @Test
    public void parseItemResponse_userDataWithNullTicks_returnsZero() throws Exception {
        String json = "{"
                + "\"Name\": \"Null Ticks\","
                + "\"Path\": \"/media/test.mkv\","
                + "\"UserData\": {"
                + "  \"PlaybackPositionTicks\": null"
                + "}"
                + "}";

        JellyfinApi.ItemResult result = JellyfinApi.parseItemResponse(json);
        assertEquals(0L, result.positionTicks);
    }
}
