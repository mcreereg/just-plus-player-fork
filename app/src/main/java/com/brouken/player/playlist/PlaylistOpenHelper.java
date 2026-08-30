package com.brouken.player.playlist;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.brouken.player.PlayerActivity;
import com.brouken.player.R;
import com.brouken.player.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Opens an M3U file: access prompts for local items, then hands off to the player queue. */
public final class PlaylistOpenHelper {

    public static final int REQUEST_ACCESS_TREE = 30;
    public static final int REQUEST_ACCESS_FILES = 31;

    private Uri playlistUri;
    private M3uPlaylist playlist;
    private List<M3uEntry> workingEntries;
    private List<M3uEntry> skippedEntries = new ArrayList<>();
    private Map<String, List<M3uEntry>> pendingGroups;
    private String pendingGroupKey;

    public boolean isActive() {
        return playlistUri != null;
    }

    public void cancel() {
        playlistUri = null;
        playlist = null;
        workingEntries = null;
        skippedEntries = new ArrayList<>();
        pendingGroups = null;
        pendingGroupKey = null;
    }

    public void begin(final PlayerActivity activity, final Uri uri) {
        cancel();
        playlistUri = uri;
        try {
            playlist = M3uReader.read(activity, uri);
        } catch (Exception e) {
            Toast.makeText(activity, R.string.playlist_open_error, Toast.LENGTH_LONG).show();
            cancel();
            return;
        }
        if (playlist.size() > PlaylistPlaybackState.softItemLimit()) {
            new AlertDialog.Builder(activity)
                    .setMessage(activity.getString(R.string.playlist_size_warning, playlist.size()))
                    .setPositiveButton(android.R.string.ok, (d, w) -> continueOpen(activity))
                    .setNegativeButton(android.R.string.cancel, (d, w) -> cancel())
                    .show();
            return;
        }
        continueOpen(activity);
    }

    private void continueOpen(final PlayerActivity activity) {
        workingEntries = new ArrayList<>(playlist.entries);
        skippedEntries = new ArrayList<>();
        resolveAndPrompt(activity);
    }

    private void resolveAndPrompt(final PlayerActivity activity) {
        final List<M3uEntry> resolved = new ArrayList<>();
        for (final M3uEntry entry : workingEntries) {
            Uri itemUri = entry.uri;
            if (itemUri == null && entry.uriString != null) {
                itemUri = M3uUriResolver.resolve(activity, playlistUri, entry.uriString);
            }
            if (itemUri == null) {
                skippedEntries.add(entry);
                continue;
            }
            resolved.add(new M3uEntry(entry.uriString, itemUri, entry.title, entry.durationSec));
        }
        workingEntries = resolved;
        final PlaylistAccessResolver.AccessResult result = PlaylistAccessResolver.partition(activity, workingEntries);
        workingEntries = new ArrayList<>(result.accessible);
        skippedEntries.addAll(result.skipped);

        if (result.skipped.isEmpty()) {
            finish(activity);
            return;
        }
        pendingGroups = PlaylistAccessResolver.groupMissingByParent(result.skipped);
        promptNextGroup(activity);
    }

    private void promptNextGroup(final PlayerActivity activity) {
        if (pendingGroups == null || pendingGroups.isEmpty()) {
            finish(activity);
            return;
        }
        Map.Entry<String, List<M3uEntry>> largest = null;
        for (final Map.Entry<String, List<M3uEntry>> entry : pendingGroups.entrySet()) {
            if (largest == null || entry.getValue().size() > largest.getValue().size()) {
                largest = entry;
            }
        }
        if (largest == null) {
            finish(activity);
            return;
        }
        pendingGroupKey = largest.getKey();
        final List<M3uEntry> group = largest.getValue();
        if (group.size() > 1) {
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
            activity.safelyStartActivityForResult(intent, REQUEST_ACCESS_TREE);
        } else {
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, Utils.supportedMimeTypesVideo);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            activity.safelyStartActivityForResult(intent, REQUEST_ACCESS_FILES);
        }
    }

    public void onAccessTreeResult(final PlayerActivity activity, final Uri treeUri) {
        if (treeUri == null || pendingGroupKey == null || pendingGroups == null) {
            finish(activity);
            return;
        }
        try {
            activity.getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        final List<M3uEntry> group = pendingGroups.remove(pendingGroupKey);
        pendingGroupKey = null;
        if (group != null) {
            final DocumentFile root = DocumentFile.fromTreeUri(activity, treeUri);
            for (final M3uEntry missing : group) {
                final DocumentFile found = findInTree(root, missing);
                if (found != null) {
                    try {
                        activity.getContentResolver().takePersistableUriPermission(found.getUri(),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {
                    }
                    workingEntries.add(new M3uEntry(missing.uriString, found.getUri(),
                            missing.title, missing.durationSec));
                } else {
                    skippedEntries.add(missing);
                }
            }
        }
        if (!pendingGroups.isEmpty()) {
            promptNextGroup(activity);
        } else {
            finish(activity);
        }
    }

    public void onAccessFilesResult(final PlayerActivity activity, final Intent data) {
        if (data == null || pendingGroupKey == null || pendingGroups == null) {
            finish(activity);
            return;
        }
        final List<M3uEntry> group = pendingGroups.remove(pendingGroupKey);
        pendingGroupKey = null;
        final List<Uri> picked = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                picked.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            picked.add(data.getData());
        }
        for (final Uri uri : picked) {
            try {
                activity.getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }
        if (group != null) {
            for (final M3uEntry missing : group) {
                Uri matched = null;
                for (final Uri uri : picked) {
                    if (namesMatch(activity, uri, missing)) {
                        matched = uri;
                        break;
                    }
                }
                if (matched != null) {
                    workingEntries.add(new M3uEntry(missing.uriString, matched,
                            missing.title, missing.durationSec));
                } else {
                    skippedEntries.add(missing);
                }
            }
        }
        if (!pendingGroups.isEmpty()) {
            promptNextGroup(activity);
        } else {
            finish(activity);
        }
    }

    public void onAccessCancelled(final PlayerActivity activity) {
        if (pendingGroups != null && pendingGroupKey != null) {
            final List<M3uEntry> group = pendingGroups.remove(pendingGroupKey);
            if (group != null) {
                skippedEntries.addAll(group);
            }
            pendingGroupKey = null;
        }
        finish(activity);
    }

    private void finish(final PlayerActivity activity) {
        if (!skippedEntries.isEmpty()) {
            final int count = skippedEntries.size();
            if (count > 3) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.playlist_skipped_items)
                        .setMessage(activity.getString(R.string.playlist_skipped_items, count))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                Toast.makeText(activity,
                        activity.getString(R.string.playlist_skipped_items, count),
                        Toast.LENGTH_LONG).show();
            }
        }
        activity.startFilePlaylistPlayback(playlistUri, playlist, workingEntries);
        cancel();
    }

    private static DocumentFile findInTree(final DocumentFile root, final M3uEntry entry) {
        if (root == null || entry.uri == null) {
            return null;
        }
        final String targetName = nameFromUri(entry.uri);
        if (targetName == null) {
            return null;
        }
        return findByName(root, targetName);
    }

    private static DocumentFile findByName(final DocumentFile dir, final String name) {
        final DocumentFile[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (final DocumentFile child : children) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child.isDirectory()) {
                final DocumentFile found = findByName(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean namesMatch(final PlayerActivity activity, final Uri picked, final M3uEntry missing) {
        final String pickedName = Utils.getFileName(activity, picked);
        final String missingName = missing.title;
        if (pickedName != null && missingName != null && pickedName.equalsIgnoreCase(missingName)) {
            return true;
        }
        final String fromUri = nameFromUri(missing.uri);
        return pickedName != null && fromUri != null && pickedName.equalsIgnoreCase(stripExt(fromUri));
    }

    private static String nameFromUri(final Uri uri) {
        final String path = uri.getPath();
        if (path == null) {
            return null;
        }
        final int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String stripExt(final String name) {
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
