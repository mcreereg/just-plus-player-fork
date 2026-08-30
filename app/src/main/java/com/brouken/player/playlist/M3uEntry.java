package com.brouken.player.playlist;

import android.net.Uri;

/** One row in an M3U playlist after URI resolution. */
public final class M3uEntry {

    public final String uriString;
    public final Uri uri;
    public final String title;
    public final int durationSec;

    public M3uEntry(final String uriString, final Uri uri, final String title, final int durationSec) {
        this.uriString = uriString;
        this.uri = uri;
        this.title = title;
        this.durationSec = durationSec;
    }
}
