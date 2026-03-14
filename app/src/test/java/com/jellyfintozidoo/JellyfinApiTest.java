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

    // RunTimeTicks parsing tests

    @Test
    public void parseItemResponse_withRunTimeTicks_returnsDuration() throws Exception {
        String json = "{"
                + "\"Name\": \"Movie\","
                + "\"Path\": \"/media/movie.mkv\","
                + "\"RunTimeTicks\": 72000000000"
                + "}";

        JellyfinApi.ItemResult result = JellyfinApi.parseItemResponse(json);
        assertEquals(72000000000L, result.durationTicks);
    }

    @Test
    public void parseItemResponse_withoutRunTimeTicks_returnsZeroDuration() throws Exception {
        String json = "{"
                + "\"Name\": \"No Duration\","
                + "\"Path\": \"/media/test.mkv\""
                + "}";

        JellyfinApi.ItemResult result = JellyfinApi.parseItemResponse(json);
        assertEquals(0L, result.durationTicks);
    }

    @Test
    public void parseItemResponse_existingFieldsStillWork() throws Exception {
        String json = "{"
                + "\"Name\": \"Regression Test\","
                + "\"Path\": \"/media/regression.mkv\","
                + "\"RunTimeTicks\": 36000000000,"
                + "\"UserData\": {"
                + "  \"PlaybackPositionTicks\": 18000000000"
                + "}"
                + "}";

        JellyfinApi.ItemResult result = JellyfinApi.parseItemResponse(json);
        assertEquals("/media/regression.mkv", result.path);
        assertEquals(18000000000L, result.positionTicks);
        assertEquals("Regression Test", result.title);
        assertEquals(36000000000L, result.durationTicks);
    }

    // --- parseItemResponse: episode-specific fields ---

    @Test
    public void parseItemResponse_episodeJson_extractsSeriesId() throws Exception {
        String json = "{"
                + "\"Name\": \"Pilot\","
                + "\"Path\": \"/media/Shows/Breaking Bad/S01E01.mkv\","
                + "\"RunTimeTicks\": 34800000000,"
                + "\"SeriesId\": \"a1b2c3d4e5f6\","
                + "\"ParentIndexNumber\": 1,"
                + "\"IndexNumber\": 1"
                + "}";

        JellyfinApi.ItemResult result = JellyfinApi.parseItemResponse(json);
        assertEquals("a1b2c3d4e5f6", result.seriesId);
        assertEquals("Pilot", result.title);
    }

    // --- parseNextUpDetailResponse tests ---

    @Test
    public void parseNextUpDetailResponse_fullJson_extractsAllFields() throws Exception {
        String json = "{"
                + "\"Items\": [{"
                + "  \"Id\": \"abc123\","
                + "  \"SeriesName\": \"Breaking Bad\","
                + "  \"Name\": \"Cat's in the Bag...\","
                + "  \"ParentIndexNumber\": 1,"
                + "  \"IndexNumber\": 2,"
                + "  \"SeriesId\": \"series-xyz\","
                + "  \"Path\": \"/media/Shows/Breaking Bad/S01E02.mkv\","
                + "  \"MediaSources\": [{"
                + "    \"Path\": \"/media/Shows/Breaking Bad/S01E02.mkv\""
                + "  }]"
                + "}],"
                + "\"TotalRecordCount\": 1"
                + "}";

        JellyfinApi.NextUpDetailResult result = JellyfinApi.parseNextUpDetailResponse(json);
        assertNotNull(result);
        assertEquals("abc123", result.itemId);
        assertEquals("Breaking Bad", result.seriesName);
        assertEquals("Cat's in the Bag...", result.episodeName);
        assertEquals(1, result.seasonNumber);
        assertEquals(2, result.episodeNumber);
        assertEquals("series-xyz", result.seriesId);
        assertEquals("/media/Shows/Breaking Bad/S01E02.mkv", result.serverPath);
    }

    @Test
    public void parseNextUpDetailResponse_missingOptionalFields_returnsDefaults() throws Exception {
        String json = "{"
                + "\"Items\": [{"
                + "  \"Id\": \"item-001\","
                + "  \"Path\": \"/media/file.mkv\""
                + "}]"
                + "}";

        JellyfinApi.NextUpDetailResult result = JellyfinApi.parseNextUpDetailResponse(json);
        assertNotNull(result);
        assertEquals("item-001", result.itemId);
        assertEquals("", result.seriesName);
        assertEquals("", result.episodeName);
        assertEquals(0, result.seasonNumber);
        assertEquals(0, result.episodeNumber);
        assertNull(result.seriesId);
        assertEquals("/media/file.mkv", result.serverPath);
    }

    @Test
    public void parseNextUpDetailResponse_emptyItems_returnsNull() throws Exception {
        String json = "{"
                + "\"Items\": [],"
                + "\"TotalRecordCount\": 0"
                + "}";

        JellyfinApi.NextUpDetailResult result = JellyfinApi.parseNextUpDetailResponse(json);
        assertNull(result);
    }

    @Test
    public void parseNextUpDetailResponse_pathInMediaSourcesFallback() throws Exception {
        String json = "{"
                + "\"Items\": [{"
                + "  \"Id\": \"item-fallback\","
                + "  \"Name\": \"Episode\","
                + "  \"MediaSources\": [{"
                + "    \"Path\": \"/media/Shows/fallback-ep.mkv\""
                + "  }]"
                + "}]"
                + "}";

        JellyfinApi.NextUpDetailResult result = JellyfinApi.parseNextUpDetailResponse(json);
        assertNotNull(result);
        assertEquals("/media/Shows/fallback-ep.mkv", result.serverPath);
    }

    // --- parseSearchByPathResponse tests ---

    @Test
    public void parseSearchByPathResponse_findsExactMatch() throws Exception {
        String json = "{"
                + "\"Items\": ["
                + "  {\"Id\": \"wrong-id\", \"Path\": \"/media/Shows/Other/ep.mkv\"},"
                + "  {\"Id\": \"correct-id\", \"Path\": \"/media/Shows/Breaking Bad/S01E02.mkv\"},"
                + "  {\"Id\": \"also-wrong\", \"Path\": \"/media/Shows/Another/ep.mkv\"}"
                + "],"
                + "\"TotalRecordCount\": 3"
                + "}";

        String result = JellyfinApi.parseSearchByPathResponse(json,
                "/media/Shows/Breaking Bad/S01E02.mkv");
        assertEquals("correct-id", result);
    }

    @Test
    public void parseSearchByPathResponse_noExactMatch_returnsNull() throws Exception {
        String json = "{"
                + "\"Items\": ["
                + "  {\"Id\": \"id-1\", \"Path\": \"/media/Shows/Other/ep.mkv\"}"
                + "],"
                + "\"TotalRecordCount\": 1"
                + "}";

        String result = JellyfinApi.parseSearchByPathResponse(json,
                "/media/Shows/Breaking Bad/S01E02.mkv");
        assertNull(result);
    }

    @Test
    public void parseSearchByPathResponse_emptyItems_returnsNull() throws Exception {
        String json = "{"
                + "\"Items\": [],"
                + "\"TotalRecordCount\": 0"
                + "}";

        String result = JellyfinApi.parseSearchByPathResponse(json,
                "/media/Shows/Breaking Bad/S01E02.mkv");
        assertNull(result);
    }

    @Test
    public void parseSearchByPathResponse_matchesMediaSourcesPath() throws Exception {
        String json = "{"
                + "\"Items\": ["
                + "  {\"Id\": \"ms-match\", \"MediaSources\": [{\"Path\": \"/media/Shows/ep.mkv\"}]}"
                + "],"
                + "\"TotalRecordCount\": 1"
                + "}";

        String result = JellyfinApi.parseSearchByPathResponse(json,
                "/media/Shows/ep.mkv");
        assertEquals("ms-match", result);
    }
}
