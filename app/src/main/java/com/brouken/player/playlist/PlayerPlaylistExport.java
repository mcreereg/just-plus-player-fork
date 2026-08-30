package com.brouken.player.playlist;

import androidx.media3.common.MediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Hands the current player queue to the playlist editor without parceling MediaItems. */
public final class PlayerPlaylistExport {

    private static List<M3uEntry> pending = Collections.emptyList();

    private PlayerPlaylistExport() {
    }

    public static void offerQueue(final List<MediaItem> items) {
        final List<M3uEntry> entries = new ArrayList<>();
        for (final MediaItem item : items) {
            if (item.localConfiguration == null || item.localConfiguration.uri == null) {
                continue;
            }
            String title = null;
            if (item.mediaMetadata != null && item.mediaMetadata.title != null) {
                title = item.mediaMetadata.title.toString();
            }
            entries.add(new M3uEntry(item.localConfiguration.uri.toString(),
                    item.localConfiguration.uri, title, -1));
        }
        pending = entries;
    }

    static List<M3uEntry> takeQueue() {
        final List<M3uEntry> copy = pending;
        pending = Collections.emptyList();
        return copy;
    }
}
