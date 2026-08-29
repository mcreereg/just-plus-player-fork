package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LastSessionTest {

    @Test
    public void roundTripKeepsClipAndTimestamp() {
        final LastSession session = new LastSession();
        session.uri = "content://com.android.externalstorage.documents/document/primary%3AMovies%2Fep2.mkv";
        session.type = "video/x-matroska";
        session.positionMs = 1_234_567L;
        session.playing = true;
        session.folderPlaylist = true;
        session.playlistIndex = 2;
        session.title = "Episode 2";

        final LastSession restored = LastSession.fromJson(session.toJson());
        assertNotNull(restored);
        assertEquals(session.uri, restored.uri);
        assertEquals(session.type, restored.type);
        assertEquals(session.positionMs, restored.positionMs);
        assertTrue(restored.playing);
        assertTrue(restored.folderPlaylist);
        assertFalse(restored.apiAccess);
        assertEquals(2, restored.playlistIndex);
        assertEquals("Episode 2", restored.title);
    }

    @Test
    public void roundTripKeepsPlaylistItemsAndEpisodePositions() {
        final LastSession session = new LastSession();
        session.uri = "https://cdn.example/s01e03.mp4";
        session.positionMs = 45_000L;
        session.apiAccess = true;
        session.playlistIndex = 2;
        session.headers = new String[]{"Referer", "https://host/", "User-Agent", "Just+"};
        session.episodePositions = new long[]{0L, 12_000L, 45_000L};

        final LastSession.Item first = new LastSession.Item();
        first.uri = "https://cdn.example/s01e01.mp4";
        first.title = "E1";
        first.poster = "https://cdn.example/e1.jpg";
        session.items.add(first);
        final LastSession.Item third = new LastSession.Item();
        third.uri = "https://cdn.example/s01e03.mp4";
        third.title = "E3";
        session.items.add(third);

        final LastSession restored = LastSession.fromJson(session.toJson());
        assertNotNull(restored);
        assertTrue(restored.apiAccess);
        assertEquals(2, restored.items.size());
        assertEquals("https://cdn.example/s01e01.mp4", restored.items.get(0).uri);
        assertEquals("E1", restored.items.get(0).title);
        assertEquals("https://cdn.example/e1.jpg", restored.items.get(0).poster);
        assertEquals("E3", restored.items.get(1).title);
        assertArrayEquals(session.headers, restored.headers);
        assertArrayEquals(session.episodePositions, restored.episodePositions);
    }

    @Test
    public void extrasJsonSurvivesRoundTrip() {
        final LastSession session = new LastSession();
        session.uri = "https://cdn.example/film.mp4";
        session.extrasJson = "{\"title\":{\"t\":\"s\",\"v\":\"Machines\"}}";

        final LastSession restored = LastSession.fromJson(session.toJson());
        assertNotNull(restored);
        assertEquals(session.extrasJson, restored.extrasJson);
    }

    @Test
    public void missingOptionalFieldsUseDefaults() {
        final LastSession restored = LastSession.fromJson("{\"uri\":\"file:///sdcard/a.mp4\"}");
        assertNotNull(restored);
        assertEquals("file:///sdcard/a.mp4", restored.uri);
        assertEquals(-1L, restored.positionMs);
        assertFalse(restored.playing);
        assertFalse(restored.folderPlaylist);
        assertFalse(restored.apiAccess);
        assertEquals(-1, restored.playlistIndex);
        assertTrue(restored.items.isEmpty());
        assertNull(restored.headers);
        assertNull(restored.episodePositions);
        assertNull(restored.extrasJson);
    }

    @Test
    public void invalidOrEmptyJsonIsRejected() {
        assertNull(LastSession.fromJson(null));
        assertNull(LastSession.fromJson(""));
        assertNull(LastSession.fromJson("{"));
        assertNull(LastSession.fromJson("{}"));
        assertNull(LastSession.fromJson("{\"title\":\"only\"}"));
    }

    @Test
    public void toJsonThenFromJsonStaysWithinFiveSecondsOfSavedPosition() {
        final LastSession session = new LastSession();
        session.uri = "file:///sdcard/film.mkv";
        session.positionMs = 3_601_250L;
        final LastSession restored = LastSession.fromJson(session.toJson());
        assertNotNull(restored);
        assertEquals(0L, Math.abs(restored.positionMs - session.positionMs));
    }
}
