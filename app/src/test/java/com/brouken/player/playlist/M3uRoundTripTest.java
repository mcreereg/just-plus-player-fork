package com.brouken.player.playlist;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class M3uRoundTripTest {

    @Test
    public void roundTripExtendedM3u() throws Exception {
        final String input = "#EXTM3U\n"
                + "#PLAYLIST:ignored\n"
                + "#EXTINF:120,Episode One\n"
                + "https://cdn.example/ep1.mp4\n"
                + "#EXTINF:-1,Episode Two\n"
                + "content://test/ep2.mkv\n";

        final M3uPlaylist parsed = M3uReader.read(null,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), null);
        assertEquals(2, parsed.size());
        assertEquals("Episode One", parsed.entries.get(0).title);
        assertEquals("https://cdn.example/ep1.mp4", parsed.entries.get(0).uri.toString());

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        M3uWriter.write(parsed, out);
        final String written = out.toString(StandardCharsets.UTF_8.name());
        assertTrue(written.startsWith("#EXTM3U\n"));
        assertFalse(written.contains("#PLAYLIST:"));
        assertTrue(written.contains("#EXTINF:120,Episode One"));
        assertTrue(written.contains("https://cdn.example/ep1.mp4"));
    }

    @Test
    public void simpleUriOnlyLines() throws Exception {
        final String input = "https://host/a.mp4\nfile:///sdcard/b.mkv\n";
        final M3uPlaylist parsed = M3uReader.read(null,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), null);
        assertEquals(2, parsed.size());
    }
}
