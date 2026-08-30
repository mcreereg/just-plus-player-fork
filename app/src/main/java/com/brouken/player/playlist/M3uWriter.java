package com.brouken.player.playlist;

import android.net.Uri;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Writes extended M3U playlists (no #PLAYLIST: tag). */
public final class M3uWriter {

    private M3uWriter() {
    }

    public static void write(final M3uPlaylist playlist, final OutputStream output) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write("#EXTM3U\n");
            final Uri base = playlist.sourceUri;
            for (final M3uEntry entry : playlist.entries) {
                final String title = entry.title != null ? entry.title : "";
                writer.write("#EXTINF:");
                writer.write(entry.durationSec >= 0 ? Integer.toString(entry.durationSec) : "-1");
                writer.write(',');
                writer.write(title);
                writer.write('\n');
                final String uriLine;
                if (base != null && entry.uri != null && M3uUriResolver.canRelativize(base, entry.uri)) {
                    uriLine = M3uUriResolver.relativize(base, entry.uri);
                } else if (entry.uri != null) {
                    uriLine = entry.uri.toString();
                } else {
                    uriLine = entry.uriString;
                }
                writer.write(uriLine);
                writer.write('\n');
            }
            writer.flush();
        }
    }
}
