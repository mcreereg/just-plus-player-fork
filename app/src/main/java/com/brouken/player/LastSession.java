package com.brouken.player;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What was playing when the process last died: the clip, how far in, whether it was playing, and the
 * folder or launcher playlist it belonged to. Written to disk on a short tick so a recents swipe
 * comes back within a couple of seconds of the close, not at the last pause.
 *
 * <p>Kept off the per-URI resume store on purpose. A launcher session must not write watch state the
 * launcher owns; this snapshot is only how the player itself finds its place again after a kill.
 */
final class LastSession {

    String uri;
    String type;
    long positionMs = -1L;
    boolean playing;
    boolean folderPlaylist;
    boolean apiAccess;
    int playlistIndex = -1;
    String title;
    String thumbnail;
    String[] headers;
    final List<Item> items = new ArrayList<>();
    long[] episodePositions;
    /** Launch extras as {@link com.brouken.player.together.SessionCodec} JSON, when this was an API session. */
    String extrasJson;

    static final class Item {
        String uri;
        String title;
        String poster;
    }

    String toJson() {
        try {
            final JSONObject json = new JSONObject();
            put(json, "uri", uri);
            put(json, "type", type);
            json.put("positionMs", positionMs);
            json.put("playing", playing);
            json.put("folder", folderPlaylist);
            json.put("api", apiAccess);
            json.put("index", playlistIndex);
            put(json, "title", title);
            put(json, "thumbnail", thumbnail);
            if (headers != null) {
                final JSONArray array = new JSONArray();
                for (String header : headers) {
                    array.put(header == null ? JSONObject.NULL : header);
                }
                json.put("headers", array);
            }
            if (!items.isEmpty()) {
                final JSONArray array = new JSONArray();
                for (Item item : items) {
                    final JSONObject row = new JSONObject();
                    put(row, "uri", item.uri);
                    put(row, "title", item.title);
                    put(row, "poster", item.poster);
                    array.put(row);
                }
                json.put("items", array);
            }
            if (episodePositions != null) {
                final JSONArray array = new JSONArray();
                for (long position : episodePositions) {
                    array.put(position);
                }
                json.put("episodePositions", array);
            }
            if (extrasJson != null && !extrasJson.isEmpty()) {
                json.put("extras", extrasJson);
            }
            return json.toString();
        } catch (Exception e) {
            return null;
        }
    }

    static LastSession fromJson(final String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            final JSONObject json = new JSONObject(text);
            final LastSession session = new LastSession();
            session.uri = optString(json, "uri");
            session.type = optString(json, "type");
            session.positionMs = json.optLong("positionMs", -1L);
            session.playing = json.optBoolean("playing", false);
            session.folderPlaylist = json.optBoolean("folder", false);
            session.apiAccess = json.optBoolean("api", false);
            session.playlistIndex = json.optInt("index", -1);
            session.title = optString(json, "title");
            session.thumbnail = optString(json, "thumbnail");
            session.headers = optStringArray(json.optJSONArray("headers"));
            final JSONArray items = json.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    final JSONObject row = items.optJSONObject(i);
                    if (row == null) {
                        continue;
                    }
                    final Item item = new Item();
                    item.uri = optString(row, "uri");
                    item.title = optString(row, "title");
                    item.poster = optString(row, "poster");
                    if (item.uri != null) {
                        session.items.add(item);
                    }
                }
            }
            session.episodePositions = optLongArray(json.optJSONArray("episodePositions"));
            session.extrasJson = optString(json, "extras");
            if (session.uri == null) {
                return null;
            }
            return session;
        } catch (Exception e) {
            return null;
        }
    }

    private static void put(final JSONObject json, final String key, final String value) throws Exception {
        if (value != null) {
            json.put(key, value);
        }
    }

    private static String optString(final JSONObject json, final String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        final String value = json.optString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }

    private static String[] optStringArray(final JSONArray array) {
        if (array == null || array.length() == 0) {
            return null;
        }
        final String[] values = new String[array.length()];
        for (int i = 0; i < array.length(); i++) {
            values[i] = array.isNull(i) ? null : array.optString(i, null);
        }
        return values;
    }

    private static long[] optLongArray(final JSONArray array) {
        if (array == null || array.length() == 0) {
            return null;
        }
        final long[] values = new long[array.length()];
        for (int i = 0; i < array.length(); i++) {
            values[i] = array.optLong(i, 0L);
        }
        return values;
    }
}
