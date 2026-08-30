package com.brouken.player.playlist;

import android.content.Context;
import android.net.Uri;

import com.brouken.player.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parsed or in-memory M3U playlist. Display name comes from the source filename only. */
public final class M3uPlaylist {

    public final Uri sourceUri;
    public final List<M3uEntry> entries;

    public M3uPlaylist(final Uri sourceUri, final List<M3uEntry> entries) {
        this.sourceUri = sourceUri;
        this.entries = entries == null ? Collections.emptyList() : new ArrayList<>(entries);
    }

    public static M3uPlaylist empty(final Uri sourceUri) {
        return new M3uPlaylist(sourceUri, Collections.emptyList());
    }

    /** Filename without extension; not stored inside the M3U body. */
    public String displayName(final Context context) {
        if (sourceUri == null) {
            return "";
        }
        final String name = Utils.getFileName(context, sourceUri);
        return name != null ? name : "";
    }

    public int size() {
        return entries.size();
    }
}
