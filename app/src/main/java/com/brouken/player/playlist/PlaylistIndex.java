package com.brouken.player.playlist;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.brouken.player.SubtitleUtils;
import com.brouken.player.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registry and discovery of playlist files. */
public final class PlaylistIndex {

    public static final String SOURCE_APP_PRIVATE = "app_private";
    public static final String SOURCE_EXTERNAL = "external";
    public static final String SOURCE_SCOPED_SCAN = "scoped_scan";

    private static final String PREF_KEY = "playlist_index_json";

    public static final class Entry {
        public final Uri uri;
        public final String title;
        public final String source;
        public final int itemCount;
        public final AccessStatus accessStatus;

        public Entry(final Uri uri, final String title, final String source,
                final int itemCount, final AccessStatus accessStatus) {
            this.uri = uri;
            this.title = title;
            this.source = source;
            this.itemCount = itemCount;
            this.accessStatus = accessStatus;
        }
    }

    public enum AccessStatus {
        FULL, PARTIAL, NONE, UNKNOWN
    }

    private PlaylistIndex() {
    }

    public static File appPrivatePlaylistsDir(final Context context) {
        final File dir = new File(context.getFilesDir(), "playlists");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static Uri appPrivateUri(final Context context, final String fileName) {
        final String safe = sanitizeFileName(fileName);
        return Uri.fromFile(new File(appPrivatePlaylistsDir(context), safe));
    }

    public static String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) {
            name = "playlist";
        }
        name = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!name.toLowerCase(Locale.US).endsWith(".m3u")) {
            name = name + ".m3u";
        }
        return name;
    }

    public static void register(final Context context, final Uri uri, final String title, final String source) {
        if (uri == null) {
            return;
        }
        final Map<String, JSONObject> map = loadRegistry(context);
        try {
            final JSONObject row = new JSONObject();
            row.put("title", title);
            row.put("source", source);
            row.put("lastOpened", System.currentTimeMillis());
            map.put(uri.toString(), row);
            saveRegistry(context, map);
        } catch (Exception ignored) {
        }
    }

    public static void unregister(final Context context, final Uri uri) {
        if (uri == null) {
            return;
        }
        final Map<String, JSONObject> map = loadRegistry(context);
        map.remove(uri.toString());
        saveRegistry(context, map);
    }

    public static List<Entry> loadAll(final Context context) {
        final Map<String, Entry> merged = new LinkedHashMap<>();
        scanAppPrivate(context, merged);
        loadRegistryEntries(context, merged);
        final Uri scopeUri = readScopeUri(context);
        if (scopeUri != null) {
            scanScoped(context, scopeUri, merged);
        }
        final List<Entry> list = new ArrayList<>(merged.values());
        list.sort(Comparator.comparing((Entry e) -> e.title, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private static void scanAppPrivate(final Context context, final Map<String, Entry> merged) {
        final File dir = appPrivatePlaylistsDir(context);
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (final File file : files) {
            if (!file.getName().toLowerCase(Locale.US).endsWith(".m3u")) {
                continue;
            }
            final Uri uri = Uri.fromFile(file);
            putEntry(context, merged, uri, Utils.getFileName(context, uri), SOURCE_APP_PRIVATE);
        }
    }

    private static void scanScoped(final Context context, final Uri scopeUri, final Map<String, Entry> merged) {
        final DocumentFile root = DocumentFile.fromTreeUri(context, scopeUri);
        if (root == null) {
            return;
        }
        scanDocumentTree(context, root, merged);
    }

    private static void scanDocumentTree(final Context context, final DocumentFile dir,
            final Map<String, Entry> merged) {
        final DocumentFile[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (final DocumentFile child : children) {
            if (child.isDirectory()) {
                scanDocumentTree(context, child, merged);
            } else {
                final String name = child.getName();
                if (name != null && name.toLowerCase(Locale.US).endsWith(".m3u")) {
                    putEntry(context, merged, child.getUri(), stripExtension(name), SOURCE_SCOPED_SCAN);
                }
            }
        }
    }

    private static void loadRegistryEntries(final Context context, final Map<String, Entry> merged) {
        for (final Map.Entry<String, JSONObject> row : loadRegistry(context).entrySet()) {
            final Uri uri = Uri.parse(row.getKey());
            final JSONObject json = row.getValue();
            final String title = json.optString("title", Utils.getFileName(context, uri));
            final String source = json.optString("source", SOURCE_EXTERNAL);
            putEntry(context, merged, uri, title, source);
        }
    }

    private static void putEntry(final Context context, final Map<String, Entry> merged,
            final Uri uri, final String title, final String source) {
        try {
            final M3uPlaylist playlist = M3uReader.read(context, uri);
            final AccessStatus status = PlaylistAccessResolver.accessStatus(context, playlist.entries);
            merged.put(uri.toString(), new Entry(uri, title, source, playlist.size(), status));
        } catch (Exception e) {
            merged.put(uri.toString(), new Entry(uri, title, source, 0, AccessStatus.UNKNOWN));
        }
    }

    private static Uri readScopeUri(final Context context) {
        final String text = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("scopeUri", null);
        return text != null ? Uri.parse(text) : null;
    }

    private static String stripExtension(final String name) {
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Map<String, JSONObject> loadRegistry(final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String text = prefs.getString(PREF_KEY, null);
        final Map<String, JSONObject> map = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return map;
        }
        try {
            final JSONObject json = new JSONObject(text);
            final JSONArray entries = json.optJSONArray("entries");
            if (entries != null) {
                for (int i = 0; i < entries.length(); i++) {
                    final JSONObject row = entries.optJSONObject(i);
                    if (row == null) {
                        continue;
                    }
                    final String uri = row.optString("uri", null);
                    if (uri != null) {
                        map.put(uri, row);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private static void saveRegistry(final Context context, final Map<String, JSONObject> map) {
        try {
            final JSONObject json = new JSONObject();
            final JSONArray entries = new JSONArray();
            for (final Map.Entry<String, JSONObject> row : map.entrySet()) {
                final JSONObject copy = new JSONObject(row.getValue().toString());
                copy.put("uri", row.getKey());
                entries.put(copy);
            }
            json.put("entries", entries);
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(PREF_KEY, json.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }
}
