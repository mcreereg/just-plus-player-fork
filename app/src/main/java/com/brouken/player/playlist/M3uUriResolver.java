package com.brouken.player.playlist;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.brouken.player.Utils;

import java.io.File;
import java.util.List;

/** Resolves relative M3U entries against the playlist file location. */
public final class M3uUriResolver {

    private M3uUriResolver() {
    }

    public static Uri resolve(final Context context, final Uri playlistUri, final String entry) {
        if (entry == null || entry.isEmpty()) {
            return null;
        }
        final String trimmed = entry.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (isAbsoluteUri(trimmed)) {
            return Uri.parse(trimmed);
        }
        if (playlistUri == null) {
            return Uri.parse(trimmed);
        }
        if (ContentResolver.SCHEME_FILE.equals(playlistUri.getScheme())) {
            final File playlistFile = new File(playlistUri.getPath());
            final File parent = playlistFile.getParentFile();
            if (parent != null) {
                return Uri.fromFile(new File(parent, trimmed));
            }
        }
        if (ContentResolver.SCHEME_CONTENT.equals(playlistUri.getScheme()) && context != null) {
            final DocumentFile playlistFile = DocumentFile.fromSingleUri(context, playlistUri);
            if (playlistFile != null) {
                final DocumentFile parent = playlistFile.getParentFile();
                if (parent != null) {
                    final String[] segments = trimmed.split("/");
                    DocumentFile current = parent;
                    for (final String segment : segments) {
                        if (segment.isEmpty() || ".".equals(segment)) {
                            continue;
                        }
                        if (current == null) {
                            break;
                        }
                        if ("..".equals(segment)) {
                            current = current.getParentFile();
                            continue;
                        }
                        final DocumentFile next = current.findFile(segment);
                        if (next != null) {
                            current = next;
                        } else {
                            final DocumentFile[] children = current.listFiles();
                            if (children != null) {
                                for (final DocumentFile child : children) {
                                    if (segment.equals(child.getName())) {
                                        current = child;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (current != null && !current.isDirectory()) {
                        return current.getUri();
                    }
                }
            }
        }
        return Uri.parse(trimmed);
    }

    public static boolean canRelativize(final Uri playlistUri, final Uri entryUri) {
        if (playlistUri == null || entryUri == null) {
            return false;
        }
        if (Utils.isSupportedNetworkUri(entryUri)) {
            return false;
        }
        if (ContentResolver.SCHEME_FILE.equals(playlistUri.getScheme())
                && ContentResolver.SCHEME_FILE.equals(entryUri.getScheme())) {
            final File playlistFile = new File(playlistUri.getPath());
            final File entryFile = new File(entryUri.getPath());
            final File parent = playlistFile.getParentFile();
            return parent != null && entryFile.getPath().startsWith(parent.getPath() + File.separator);
        }
        if (ContentResolver.SCHEME_CONTENT.equals(playlistUri.getScheme())
                && ContentResolver.SCHEME_CONTENT.equals(entryUri.getScheme())) {
            final String playlistPath = playlistUri.getPath();
            final String entryPath = entryUri.getPath();
            if (playlistPath == null || entryPath == null) {
                return false;
            }
            final int lastSlash = playlistPath.lastIndexOf('/');
            if (lastSlash < 0) {
                return false;
            }
            final String parentPath = playlistPath.substring(0, lastSlash + 1);
            return entryPath.startsWith(parentPath);
        }
        return false;
    }

    public static String relativize(final Uri playlistUri, final Uri entryUri) {
        if (!canRelativize(playlistUri, entryUri)) {
            return entryUri.toString();
        }
        if (ContentResolver.SCHEME_FILE.equals(playlistUri.getScheme())) {
            final File playlistFile = new File(playlistUri.getPath());
            final File entryFile = new File(entryUri.getPath());
            final File parent = playlistFile.getParentFile();
            if (parent != null) {
                return parent.toURI().relativize(entryFile.toURI()).getPath();
            }
        }
        if (ContentResolver.SCHEME_CONTENT.equals(playlistUri.getScheme())) {
            final String playlistPath = playlistUri.getPath();
            final String entryPath = entryUri.getPath();
            if (playlistPath != null && entryPath != null) {
                final int lastSlash = playlistPath.lastIndexOf('/');
                if (lastSlash >= 0) {
                    final String parentPath = playlistPath.substring(0, lastSlash + 1);
                    if (entryPath.startsWith(parentPath)) {
                        return entryPath.substring(parentPath.length());
                    }
                }
            }
        }
        return entryUri.toString();
    }

    private static boolean isAbsoluteUri(final String value) {
        final String lower = value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("rtsp://") || lower.startsWith("content://")
                || lower.startsWith("file://");
    }
}
