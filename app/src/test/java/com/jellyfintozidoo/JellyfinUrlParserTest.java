package com.jellyfintozidoo;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for JellyfinApi.extractItemId() -- parsing Jellyfin streaming URLs.
 */
public class JellyfinUrlParserTest {

    @Test
    public void extractItemId_withHyphens_returnsUuid() {
        String url = "http://192.168.1.10:8096/Videos/550e8400-e29b-41d4-a716-446655440000/stream?static=true&api_key=abc";
        String result = JellyfinApi.extractItemId(url);
        assertEquals("550e8400-e29b-41d4-a716-446655440000", result);
    }

    @Test
    public void extractItemId_withoutHyphens_returnsUuid() {
        String url = "http://server:8096/Videos/550e8400e29b41d4a716446655440000/stream";
        String result = JellyfinApi.extractItemId(url);
        assertEquals("550e8400e29b41d4a716446655440000", result);
    }

    @Test
    public void extractItemId_caseInsensitive_returnsUuid() {
        String url = "http://server:8096/videos/ABCDEF01-2345-6789-ABCD-EF0123456789/STREAM";
        String result = JellyfinApi.extractItemId(url);
        assertNotNull(result);
        assertEquals("ABCDEF01-2345-6789-ABCD-EF0123456789", result);
    }

    @Test
    public void extractItemId_plexUrl_returnsNull() {
        String url = "https://plex.server.com/library/parts/12345/file.mkv";
        assertNull(JellyfinApi.extractItemId(url));
    }

    @Test
    public void extractItemId_plainFilePath_returnsNull() {
        String url = "/mnt/media/movie.mkv";
        assertNull(JellyfinApi.extractItemId(url));
    }

    @Test
    public void extractItemId_withQueryParams_returnsUuid() {
        String url = "http://server:8096/Videos/abcdef01-2345-6789-abcd-ef0123456789/stream?static=true&mediaSourceId=xyz&api_key=token123";
        String result = JellyfinApi.extractItemId(url);
        assertEquals("abcdef01-2345-6789-abcd-ef0123456789", result);
    }

    @Test
    public void extractItemId_nullUrl_returnsNull() {
        assertNull(JellyfinApi.extractItemId(null));
    }
}
