package com.jellyfintozidoo;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for media segment response parsing.
 * Tests parseIntroSkipperResponse() / parseMediaSegmentsResponse() and the
 * IntroSkipperResult accessors.
 *
 * The body shape is Jellyfin 12.0's GET /MediaSegments/{itemId}, a
 * MediaSegmentDtoQueryResult:
 * {"Items":[{"Id","ItemId","Type","StartTicks","EndTicks"}],"TotalRecordCount":n}
 * where Type is a MediaSegmentType (Unknown, Commercial, Preview, Recap, Outro, Intro)
 * and the tick fields are .NET ticks at 10,000 per millisecond.
 * Intro Skipper 12.0 maps its Introduction mode to Intro and its Credits mode to Outro.
 */
public class IntroSkipperApiTest {

    /** 10,000 ticks per millisecond. */
    private static long ms(long milliseconds) {
        return milliseconds * 10000L;
    }

    private static String segment(String type, long startTicks, long endTicks) {
        return "{"
                + "\"Id\": \"11111111111111111111111111111111\","
                + "\"ItemId\": \"22222222222222222222222222222222\","
                + "\"Type\": \"" + type + "\","
                + "\"StartTicks\": " + startTicks + ","
                + "\"EndTicks\": " + endTicks
                + "}";
    }

    private static String queryResult(String... segments) {
        StringBuilder sb = new StringBuilder("{\"Items\": [");
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(segments[i]);
        }
        sb.append("], \"TotalRecordCount\": ").append(segments.length).append("}");
        return sb.toString();
    }

    // Happy path

    @Test
    public void parse_introAndOutro_returnsAllTimestamps() {
        String json = queryResult(
                segment("Intro", ms(30500), ms(90250)),
                segment("Outro", ms(1313264), ms(1408264)));

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(30500L, result.introStartMs());
        assertEquals(90250L, result.introEndMs());
        assertEquals(1313264L, result.creditStartMs());
        assertEquals(1408264L, result.creditEndMs());
    }

    @Test
    public void parse_onlyIntro_creditsAreNegativeOne() {
        String json = queryResult(segment("Intro", ms(10000), ms(50000)));

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(10000L, result.introStartMs());
        assertEquals(50000L, result.introEndMs());
        assertEquals(-1L, result.creditStartMs());
        assertEquals(-1L, result.creditEndMs());
    }

    @Test
    public void parse_onlyOutro_introIsNegativeOne() {
        String json = queryResult(segment("Outro", ms(1200000), ms(1350000)));

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
        assertEquals(1200000L, result.creditStartMs());
        assertEquals(1350000L, result.creditEndMs());
    }

    @Test
    public void parse_ticksConvertToMilliseconds() {
        // 145.792 seconds is 145792 ms is 1457920000 ticks
        String json = queryResult(segment("Intro", 1457920000L, 2284970000L));

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(145792L, result.introStartMs());
        assertEquals(228497L, result.introEndMs());
    }

    @Test
    public void parse_ignoresOtherSegmentTypes() {
        String json = queryResult(
                segment("Commercial", ms(1000), ms(2000)),
                segment("Preview", ms(3000), ms(4000)),
                segment("Recap", ms(5000), ms(6000)),
                segment("Unknown", ms(7000), ms(8000)));

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
        assertEquals(-1L, result.creditStartMs());
        assertEquals(-1L, result.creditEndMs());
    }

    // Several segments of one type: first by StartTicks wins

    @Test
    public void parse_multipleIntros_takesLowestStartTicks() {
        String json = queryResult(
                segment("Intro", ms(200000), ms(260000)),
                segment("Intro", ms(30000), ms(75000)),
                segment("Intro", ms(500000), ms(560000)));

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(30000L, result.introStartMs());
        assertEquals(75000L, result.introEndMs());
    }

    @Test
    public void parse_multipleOutros_takesLowestStartTicks() {
        String json = queryResult(
                segment("Outro", ms(1400000), ms(1450000)),
                segment("Outro", ms(1300000), ms(1360000)));

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(1300000L, result.creditStartMs());
        assertEquals(1360000L, result.creditEndMs());
    }

    @Test
    public void parse_typeMatchIsCaseInsensitive() {
        String json = queryResult(
                segment("intro", ms(1000), ms(2000)),
                segment("OUTRO", ms(9000), ms(9500)));

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(1000L, result.introStartMs());
        assertEquals(2000L, result.introEndMs());
        assertEquals(9000L, result.creditStartMs());
        assertEquals(9500L, result.creditEndMs());
    }

    // Empty and absent results

    @Test
    public void parse_emptyItems_allNegativeOne() {
        String json = "{\"Items\": [], \"TotalRecordCount\": 0}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
        assertEquals(-1L, result.creditStartMs());
        assertEquals(-1L, result.creditEndMs());
    }

    @Test
    public void parse_emptyJsonObject_allNegativeOne() {
        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse("{}");
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
        assertEquals(-1L, result.creditStartMs());
        assertEquals(-1L, result.creditEndMs());
    }

    @Test
    public void parse_itemsIsNull_allNegativeOne() {
        String json = "{\"Items\": null, \"TotalRecordCount\": 0}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.creditStartMs());
    }

    // Hostile bodies: the parser must never throw

    @Test
    public void parse_htmlErrorPage_allNegativeOne() {
        String html = "<html><head><title>404 Not Found</title></head>"
                + "<body><h1>Not Found</h1></body></html>";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(html);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
        assertEquals(-1L, result.creditStartMs());
        assertEquals(-1L, result.creditEndMs());
    }

    @Test
    public void parse_jsonArrayRoot_allNegativeOne() {
        String json = "[" + segment("Intro", 100000L, 200000L) + "]";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.creditStartMs());
    }

    @Test
    public void parse_nullBody_allNegativeOne() {
        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(null);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
        assertEquals(-1L, result.creditStartMs());
        assertEquals(-1L, result.creditEndMs());
    }

    @Test
    public void parse_emptyBody_allNegativeOne() {
        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse("");
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.creditStartMs());
    }

    @Test
    public void parse_nullEndTicks_segmentIgnored() {
        String json = "{\"Items\": [{"
                + "\"Type\": \"Intro\","
                + "\"StartTicks\": 100000,"
                + "\"EndTicks\": null"
                + "}], \"TotalRecordCount\": 1}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
    }

    @Test
    public void parse_zeroEndTicks_segmentIgnored() {
        String json = queryResult(
                segment("Intro", ms(10000), 0L),
                segment("Outro", ms(1200000), ms(1350000)));

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
        assertEquals(1200000L, result.creditStartMs());
        assertEquals(1350000L, result.creditEndMs());
    }

    @Test
    public void parse_missingTickFields_segmentIgnored() {
        String json = "{\"Items\": [{\"Type\": \"Intro\"}], \"TotalRecordCount\": 1}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.introEndMs());
    }

    @Test
    public void parse_missingType_segmentIgnored() {
        String json = "{\"Items\": [{\"StartTicks\": 100000, \"EndTicks\": 200000}],"
                + " \"TotalRecordCount\": 1}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.creditStartMs());
    }

    @Test
    public void parse_nonObjectElements_areSkippedNotFatal() {
        String json = "{\"Items\": [null, 42, \"text\", [1,2],"
                + segment("Intro", ms(4000), ms(9000))
                + "], \"TotalRecordCount\": 5}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(4000L, result.introStartMs());
        assertEquals(9000L, result.introEndMs());
    }

    @Test
    public void parse_ticksAsStrings_doNotBreakOtherSegments() {
        String json = "{\"Items\": ["
                + "{\"Type\": \"Intro\", \"StartTicks\": \"not-a-number\", \"EndTicks\": 200000},"
                + segment("Outro", ms(1200000), ms(1350000))
                + "], \"TotalRecordCount\": 2}";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(1200000L, result.creditStartMs());
        assertEquals(1350000L, result.creditEndMs());
    }

    @Test
    public void parse_truncatedJson_allNegativeOne() {
        String json = "{\"Items\": [{\"Type\": \"Intro\", \"StartTicks\": 100";

        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(json);
        assertEquals(-1L, result.introStartMs());
        assertEquals(-1L, result.creditStartMs());
    }

    // Alias and URL builder

    @Test
    public void parseIntroSkipperResponse_isAliasOfParseMediaSegmentsResponse() {
        String json = queryResult(
                segment("Intro", ms(1000), ms(2000)),
                segment("Outro", ms(3000), ms(4000)));

        JellyfinApi.IntroSkipperResult viaAlias = JellyfinApi.parseIntroSkipperResponse(json);
        JellyfinApi.IntroSkipperResult direct = JellyfinApi.parseMediaSegmentsResponse(json);

        assertEquals(direct.introStartMs(), viaAlias.introStartMs());
        assertEquals(direct.introEndMs(), viaAlias.introEndMs());
        assertEquals(direct.creditStartMs(), viaAlias.creditStartMs());
        assertEquals(direct.creditEndMs(), viaAlias.creditEndMs());
    }

    @Test
    public void buildMediaSegmentsUrl_usesNativeRouteWithTypeFilter() {
        String url = JellyfinApi.buildMediaSegmentsUrl(
                "http://server:8096", "abc123");
        assertEquals("http://server:8096/MediaSegments/abc123"
                + "?includeSegmentTypes=Intro&includeSegmentTypes=Outro", url);
    }

    @Test
    public void buildMediaSegmentsUrl_stripsTrailingSlash() {
        String url = JellyfinApi.buildMediaSegmentsUrl(
                "http://server:8096/", "abc123");
        assertTrue(url.startsWith("http://server:8096/MediaSegments/abc123"));
    }

    @Test
    public void buildMediaSegmentsUrl_hasNoRemovedPluginRoute() {
        String url = JellyfinApi.buildMediaSegmentsUrl("http://server:8096", "abc123");
        assertFalse(url.contains("IntroSkipperSegments"));
        assertFalse(url.contains("/Episode/"));
    }
}
