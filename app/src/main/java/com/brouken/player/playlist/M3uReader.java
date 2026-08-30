package com.brouken.player.playlist;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses extended M3U playlists. Ignores foreign #PLAYLIST: tags. */
public final class M3uReader {

    private static final Pattern EXTINF = Pattern.compile("#EXTINF:([^,]*),(.*)");

    private M3uReader() {
    }

    public static M3uPlaylist read(final Context context, final InputStream input, final Uri sourceUri)
            throws IOException {
        final List<M3uEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            String pendingTitle = null;
            int pendingDuration = -1;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#")) {
                    if ("#EXTM3U".equalsIgnoreCase(line)) {
                        continue;
                    }
                    if (line.toUpperCase().startsWith("#PLAYLIST:")) {
                        continue;
                    }
                    final Matcher matcher = EXTINF.matcher(line);
                    if (matcher.matches()) {
                        pendingDuration = parseDuration(matcher.group(1));
                        pendingTitle = matcher.group(2).trim();
                    }
                    continue;
                }
                final Uri resolved = M3uUriResolver.resolve(context, sourceUri, line);
                if (resolved == null) {
                    pendingTitle = null;
                    pendingDuration = -1;
                    continue;
                }
                String title = pendingTitle;
                if (title == null || title.isEmpty()) {
                    title = fileNameFromUri(resolved);
                }
                entries.add(new M3uEntry(line, resolved, title, pendingDuration));
                pendingTitle = null;
                pendingDuration = -1;
            }
        }
        return new M3uPlaylist(sourceUri, entries);
    }

    public static M3uPlaylist read(final Context context, final Uri sourceUri) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(sourceUri)) {
            if (input == null) {
                throw new IOException("Cannot open " + sourceUri);
            }
            return read(context, input, sourceUri);
        }
    }

    /** True when the URI or MIME type indicates a file playlist (not an HLS stream). */
    public static boolean isPlaylistFile(final Uri uri, final String mimeType) {
        if (uri == null) {
            return false;
        }
        final String path = uri.getPath();
        if (path != null && path.toLowerCase().endsWith(".m3u")) {
            return true;
        }
        if (mimeType != null) {
            final String lower = mimeType.toLowerCase();
            if (lower.contains("mpegurl") && !path.toLowerCase().endsWith(".m3u8")) {
                return true;
            }
        }
        return false;
    }

    /** Sniff content: HLS manifests start with #EXT-X-; file playlists use #EXTINF or bare URIs. */
    public static boolean sniffIsFilePlaylist(final Context context, final Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return false;
            }
            final byte[] header = new byte[512];
            final int read = input.read(header);
            if (read <= 0) {
                return false;
            }
            final String text = new String(header, 0, read, StandardCharsets.UTF_8).toUpperCase();
            if (text.contains("#EXT-X-")) {
                return false;
            }
            return text.contains("#EXTM3U") || text.contains("#EXTINF:");
        } catch (IOException e) {
            return false;
        }
    }

    private static int parseDuration(final String raw) {
        if (raw == null || raw.isEmpty() || "-1".equals(raw.trim())) {
            return -1;
        }
        try {
            final double value = Double.parseDouble(raw.trim());
            return (int) Math.round(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String fileNameFromUri(final Uri uri) {
        final String path = uri.getPath();
        if (path == null) {
            return uri.toString();
        }
        final int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        final int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }
}
