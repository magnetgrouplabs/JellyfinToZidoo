package com.jellyfintozidoo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for MediaStream index mapping and URL parameter extraction.
 * Tests jellyfinToZidooAudioIndex, jellyfinToZidooSubtitleIndex,
 * parseUrlParam, and findDefaultStreamIndex.
 */
public class MediaStreamParsingTest {

    // --- Helper to build a MediaStream JsonObject ---
    private JsonObject stream(int index, String type) {
        JsonObject obj = new JsonObject();
        obj.addProperty("Index", index);
        obj.addProperty("Type", type);
        return obj;
    }

    private JsonObject stream(int index, String type, boolean isDefault, boolean isForced) {
        JsonObject obj = stream(index, type);
        obj.addProperty("IsDefault", isDefault);
        obj.addProperty("IsForced", isForced);
        return obj;
    }

    // --- jellyfinToZidooAudioIndex tests ---

    @Test
    public void audioIndex_firstAudio_returnsZero() {
        JsonArray streams = new JsonArray();
        streams.add(stream(0, "Video"));
        streams.add(stream(1, "Audio"));
        streams.add(stream(2, "Audio"));
        streams.add(stream(3, "Subtitle"));

        assertEquals(0, JellyfinApi.jellyfinToZidooAudioIndex(streams, 1));
    }

    @Test
    public void audioIndex_secondAudio_returnsOne() {
        JsonArray streams = new JsonArray();
        streams.add(stream(0, "Video"));
        streams.add(stream(1, "Audio"));
        streams.add(stream(2, "Audio"));
        streams.add(stream(3, "Subtitle"));

        assertEquals(1, JellyfinApi.jellyfinToZidooAudioIndex(streams, 2));
    }

    @Test
    public void audioIndex_videoStream_returnsNegativeOne() {
        JsonArray streams = new JsonArray();
        streams.add(stream(0, "Video"));
        streams.add(stream(1, "Audio"));

        assertEquals(-1, JellyfinApi.jellyfinToZidooAudioIndex(streams, 0));
    }

    @Test
    public void audioIndex_notFound_returnsNegativeOne() {
        JsonArray streams = new JsonArray();
        streams.add(stream(0, "Video"));
        streams.add(stream(1, "Audio"));

        assertEquals(-1, JellyfinApi.jellyfinToZidooAudioIndex(streams, 5));
    }

    @Test
    public void audioIndex_emptyArray_returnsNegativeOne() {
        JsonArray streams = new JsonArray();
        assertEquals(-1, JellyfinApi.jellyfinToZidooAudioIndex(streams, 0));
    }

    // --- jellyfinToZidooSubtitleIndex tests ---

    @Test
    public void subtitleIndex_firstSubtitle_returnsOne() {
        JsonArray streams = new JsonArray();
        streams.add(stream(0, "Video"));
        streams.add(stream(1, "Audio"));
        streams.add(stream(2, "Subtitle"));
        streams.add(stream(3, "Subtitle"));

        assertEquals(1, JellyfinApi.jellyfinToZidooSubtitleIndex(streams, 2));
    }

    @Test
    public void subtitleIndex_secondSubtitle_returnsTwo() {
        JsonArray streams = new JsonArray();
        streams.add(stream(0, "Video"));
        streams.add(stream(1, "Audio"));
        streams.add(stream(2, "Subtitle"));
        streams.add(stream(3, "Subtitle"));

        assertEquals(2, JellyfinApi.jellyfinToZidooSubtitleIndex(streams, 3));
    }

    @Test
    public void subtitleIndex_audioStream_returnsNegativeOne() {
        JsonArray streams = new JsonArray();
        streams.add(stream(0, "Video"));
        streams.add(stream(1, "Audio"));
        streams.add(stream(2, "Subtitle"));

        assertEquals(-1, JellyfinApi.jellyfinToZidooSubtitleIndex(streams, 1));
    }

    @Test
    public void subtitleIndex_emptyArray_returnsNegativeOne() {
        JsonArray streams = new JsonArray();
        assertEquals(-1, JellyfinApi.jellyfinToZidooSubtitleIndex(streams, 0));
    }

    // --- parseUrlParam tests ---

    @Test
    public void parseUrlParam_audioStreamIndex_returnsValue() {
        String url = "http://server:8096/Videos/abc123/stream?static=true&AudioStreamIndex=2&SubtitleStreamIndex=3";
        assertEquals(2, JellyfinApi.parseUrlParam(url, "AudioStreamIndex"));
    }

    @Test
    public void parseUrlParam_subtitleStreamIndex_returnsValue() {
        String url = "http://server:8096/Videos/abc123/stream?static=true&AudioStreamIndex=2&SubtitleStreamIndex=3";
        assertEquals(3, JellyfinApi.parseUrlParam(url, "SubtitleStreamIndex"));
    }

    @Test
    public void parseUrlParam_paramMissing_returnsNegativeOne() {
        String url = "http://server:8096/Videos/abc123/stream?static=true";
        assertEquals(-1, JellyfinApi.parseUrlParam(url, "AudioStreamIndex"));
    }

    @Test
    public void parseUrlParam_malformedUrl_returnsNegativeOne() {
        assertEquals(-1, JellyfinApi.parseUrlParam("not a url at all", "AudioStreamIndex"));
    }

    // --- findDefaultStreamIndex tests ---

    @Test
    public void findDefaultStreamIndex_defaultAudio_returnsIndex() {
        JsonArray streams = new JsonArray();
        streams.add(stream(0, "Video", false, false));
        streams.add(stream(1, "Audio", false, false));
        streams.add(stream(2, "Audio", true, false));
        streams.add(stream(3, "Subtitle", false, false));

        assertEquals(2, JellyfinApi.findDefaultStreamIndex(streams, "Audio"));
    }

    @Test
    public void findDefaultStreamIndex_noDefaultAudio_returnsNegativeOne() {
        JsonArray streams = new JsonArray();
        streams.add(stream(0, "Video", false, false));
        streams.add(stream(1, "Audio", false, false));
        streams.add(stream(2, "Audio", false, false));

        assertEquals(-1, JellyfinApi.findDefaultStreamIndex(streams, "Audio"));
    }

    @Test
    public void findDefaultStreamIndex_forcedSubtitle_returnsIndex() {
        JsonArray streams = new JsonArray();
        streams.add(stream(0, "Video", false, false));
        streams.add(stream(1, "Audio", true, false));
        streams.add(stream(2, "Subtitle", false, false));
        streams.add(stream(3, "Subtitle", false, true));

        assertEquals(3, JellyfinApi.findDefaultStreamIndex(streams, "Subtitle"));
    }

    @Test
    public void findDefaultStreamIndex_noMatchingType_returnsNegativeOne() {
        JsonArray streams = new JsonArray();
        streams.add(stream(0, "Video", false, false));
        streams.add(stream(1, "Audio", true, false));

        assertEquals(-1, JellyfinApi.findDefaultStreamIndex(streams, "Subtitle"));
    }
}
