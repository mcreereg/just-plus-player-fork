package com.brouken.player.playlist;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.brouken.player.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Checks local read access and groups missing items by parent for SAF prompts. */
public final class PlaylistAccessResolver {

    public static final class AccessResult {
        public final List<M3uEntry> accessible;
        public final List<M3uEntry> skipped;

        public AccessResult(final List<M3uEntry> accessible, final List<M3uEntry> skipped) {
            this.accessible = accessible;
            this.skipped = skipped;
        }
    }

    private PlaylistAccessResolver() {
    }

    public static boolean isNetworkEntry(final M3uEntry entry) {
        return entry != null && entry.uri != null && Utils.isSupportedNetworkUri(entry.uri);
    }

    public static boolean canReadLocal(final Context context, final Uri uri) {
        if (uri == null || Utils.isSupportedNetworkUri(uri)) {
            return true;
        }
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            final String path = uri.getPath();
            return path != null && new File(path).canRead();
        }
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try {
                if (context.getContentResolver().openInputStream(uri) != null) {
                    return true;
                }
            } catch (Exception ignored) {
            }
            return false;
        }
        return false;
    }

    public static PlaylistIndex.AccessStatus accessStatus(final Context context, final List<M3uEntry> entries) {
        int localTotal = 0;
        int localReadable = 0;
        for (final M3uEntry entry : entries) {
            if (isNetworkEntry(entry)) {
                continue;
            }
            localTotal++;
            if (canReadLocal(context, entry.uri)) {
                localReadable++;
            }
        }
        if (localTotal == 0) {
            return PlaylistIndex.AccessStatus.FULL;
        }
        if (localReadable == 0) {
            return PlaylistIndex.AccessStatus.NONE;
        }
        if (localReadable < localTotal) {
            return PlaylistIndex.AccessStatus.PARTIAL;
        }
        return PlaylistIndex.AccessStatus.FULL;
    }

    public static AccessResult partition(final Context context, final List<M3uEntry> entries) {
        final List<M3uEntry> accessible = new ArrayList<>();
        final List<M3uEntry> skipped = new ArrayList<>();
        for (final M3uEntry entry : entries) {
            if (isNetworkEntry(entry) || canReadLocal(context, entry.uri)) {
                accessible.add(entry);
            } else {
                skipped.add(entry);
            }
        }
        return new AccessResult(accessible, skipped);
    }

    /** Groups missing local entries by parent path key for batched prompts. */
    public static Map<String, List<M3uEntry>> groupMissingByParent(final List<M3uEntry> missing) {
        final Map<String, List<M3uEntry>> groups = new LinkedHashMap<>();
        for (final M3uEntry entry : missing) {
            if (entry.uri == null) {
                continue;
            }
            final String key = parentKey(entry.uri);
            List<M3uEntry> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(key, list);
            }
            list.add(entry);
        }
        return groups;
    }

    public static List<M3uEntry> sortGroupsLargestFirst(final Map<String, List<M3uEntry>> groups) {
        final List<Map.Entry<String, List<M3uEntry>>> list = new ArrayList<>(groups.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));
        final List<M3uEntry> ordered = new ArrayList<>();
        for (final Map.Entry<String, List<M3uEntry>> entry : list) {
            ordered.addAll(entry.getValue());
        }
        return ordered;
    }

    private static String parentKey(final Uri uri) {
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            final File file = new File(uri.getPath());
            final File parent = file.getParentFile();
            return parent != null ? parent.getAbsolutePath() : uri.toString();
        }
        final String path = uri.getPath();
        if (path == null) {
            return uri.toString();
        }
        final int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(0, slash) : path;
    }
}
