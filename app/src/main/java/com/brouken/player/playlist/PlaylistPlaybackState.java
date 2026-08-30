package com.brouken.player.playlist;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Persists per-playlist current index and last-played playlist across restarts. */
public final class PlaylistPlaybackState {

    private static final String FILE_NAME = "playlist_state";
    private static final int SOFT_ITEM_LIMIT = 100;

    private final Context context;
    private String lastPlaylistUri;
    private int lastPlaylistIndex = -1;
    private final Map<String, Integer> indices = new HashMap<>();

    public PlaylistPlaybackState(final Context context) {
        this.context = context.getApplicationContext();
        load();
    }

    public static int softItemLimit() {
        return SOFT_ITEM_LIMIT;
    }

    public int getIndex(final Uri playlistUri) {
        if (playlistUri == null) {
            return 0;
        }
        final Integer value = indices.get(playlistUri.toString());
        return value != null ? value : 0;
    }

    public void setIndex(final Uri playlistUri, final int index) {
        if (playlistUri == null) {
            return;
        }
        final int clamped = Math.max(0, index);
        indices.put(playlistUri.toString(), clamped);
        lastPlaylistUri = playlistUri.toString();
        lastPlaylistIndex = clamped;
        save();
    }

    public Uri getLastPlaylistUri() {
        return lastPlaylistUri != null ? Uri.parse(lastPlaylistUri) : null;
    }

    public int getLastPlaylistIndex() {
        return lastPlaylistIndex;
    }

    public void setLastPlaylist(final Uri playlistUri, final int index) {
        if (playlistUri == null) {
            return;
        }
        lastPlaylistUri = playlistUri.toString();
        lastPlaylistIndex = Math.max(0, index);
        indices.put(lastPlaylistUri, lastPlaylistIndex);
        save();
    }

    private void load() {
        try (FileInputStream fis = context.openFileInput(FILE_NAME);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            final StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
            if (text.length() == 0) {
                return;
            }
            final JSONObject json = new JSONObject(text.toString());
            lastPlaylistUri = json.optString("lastPlaylistUri", null);
            if (lastPlaylistUri != null && lastPlaylistUri.isEmpty()) {
                lastPlaylistUri = null;
            }
            lastPlaylistIndex = json.optInt("lastPlaylistIndex", -1);
            final JSONObject playlists = json.optJSONObject("playlists");
            if (playlists != null) {
                final Iterator<String> keys = playlists.keys();
                while (keys.hasNext()) {
                    final String key = keys.next();
                    final JSONObject row = playlists.optJSONObject(key);
                    if (row != null) {
                        indices.put(key, row.optInt("index", 0));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try {
            final JSONObject json = new JSONObject();
            if (lastPlaylistUri != null) {
                json.put("lastPlaylistUri", lastPlaylistUri);
            }
            json.put("lastPlaylistIndex", lastPlaylistIndex);
            final JSONObject playlists = new JSONObject();
            for (final Map.Entry<String, Integer> entry : indices.entrySet()) {
                final JSONObject row = new JSONObject();
                row.put("index", entry.getValue());
                playlists.put(entry.getKey(), row);
            }
            json.put("playlists", playlists);
            try (FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)) {
                fos.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }
}
