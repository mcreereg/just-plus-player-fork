package com.brouken.player.playlist;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.brouken.player.PlayerActivity;
import com.brouken.player.R;
import com.brouken.player.SubtitleUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaylistEditorActivity extends AppCompatActivity {

    public static final String EXTRA_PLAYLIST_URI = "playlist_uri";
    public static final String EXTRA_SAVE_QUEUE = "save_queue";

    private static final int REQUEST_FOLDER_ADD = 32;
    private static final int REQUEST_COPY = 33;

    private RecyclerView recyclerView;
    private boolean editingEntries;
    private Uri editingUri;
    private final List<M3uEntry> editingEntries = new ArrayList<>();
    private EntryAdapter entryAdapter;
    private ListAdapter listAdapter;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (PlayerActivity.isTvBox) {
            finish();
            return;
        }
        setContentView(R.layout.activity_playlist_editor);
        final Toolbar toolbar = findViewById(R.id.playlist_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        recyclerView = findViewById(R.id.playlist_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (getIntent().getBooleanExtra(EXTRA_SAVE_QUEUE, false)) {
            loadQueueFromPlayer();
            showEntryEditor(PlaylistIndex.appPrivateUri(this,
                    "saved_" + System.currentTimeMillis()));
            return;
        }
        final String uriText = getIntent().getStringExtra(EXTRA_PLAYLIST_URI);
        if (uriText != null) {
            openEditor(Uri.parse(uriText));
            return;
        }
        showPlaylistList();
    }

    private void showPlaylistList() {
        editingEntries = false;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.playlist_editor);
        }
        final List<PlaylistIndex.Entry> entries = PlaylistIndex.loadAll(this);
        listAdapter = new ListAdapter(entries);
        recyclerView.setAdapter(listAdapter);
        invalidateOptionsMenu();
    }

    private void openEditor(final Uri uri) {
        try {
            final M3uPlaylist playlist = M3uReader.read(this, uri);
            editingEntries.clear();
            editingEntries.addAll(playlist.entries);
            showEntryEditor(uri);
        } catch (Exception e) {
            Toast.makeText(this, R.string.playlist_open_error, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void showEntryEditor(final Uri uri) {
        editingEntries = true;
        editingUri = uri;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(new M3uPlaylist(uri, editingEntries).displayName(this));
        }
        entryAdapter = new EntryAdapter();
        recyclerView.setAdapter(entryAdapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                final int from = viewHolder.getBindingAdapterPosition();
                final int to = target.getBindingAdapterPosition();
                if (from < 0 || to < 0) {
                    return false;
                }
                Collections.swap(editingEntries, from, to);
                entryAdapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        }).attachToRecyclerView(recyclerView);
        invalidateOptionsMenu();
    }

    private void loadQueueFromPlayer() {
        editingEntries.clear();
        // Filled when launched from PlayerActivity via static hand-off
        editingEntries.addAll(PlayerPlaylistExport.takeQueue());
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (!editingEntries) {
            getMenuInflater().inflate(R.menu.menu_playlist_list, menu);
        } else {
            getMenuInflater().inflate(R.menu.menu_playlist_edit, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            if (editingEntries && getIntent().getStringExtra(EXTRA_PLAYLIST_URI) == null
                    && !getIntent().getBooleanExtra(EXTRA_SAVE_QUEUE, false)) {
                showPlaylistList();
            } else {
                finish();
            }
            return true;
        }
        if (id == R.id.action_new_playlist) {
            promptNewPlaylist();
            return true;
        }
        if (id == R.id.action_save_playlist) {
            saveCurrentPlaylist();
            return true;
        }
        if (id == R.id.action_add_folder) {
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, REQUEST_FOLDER_ADD);
            return true;
        }
        if (id == R.id.action_play_playlist) {
            playCurrent();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void promptNewPlaylist() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(R.string.playlist_name_prompt)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    final String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    final Uri uri = PlaylistIndex.appPrivateUri(this, name);
                    editingEntries.clear();
                    showEntryEditor(uri);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveCurrentPlaylist() {
        if (editingEntries.size() > PlaylistPlaybackState.softItemLimit()) {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.playlist_size_warning, editingEntries.size()))
                    .setPositiveButton(android.R.string.ok, (d, w) -> writePlaylist())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        writePlaylist();
    }

    private void writePlaylist() {
        try {
            final M3uPlaylist playlist = new M3uPlaylist(editingUri, editingEntries);
            final OutputStream output;
            if (ContentResolver.SCHEME_FILE.equals(editingUri.getScheme())) {
                output = new FileOutputStream(editingUri.getPath());
            } else {
                output = getContentResolver().openOutputStream(editingUri, "wt");
            }
            if (output == null) {
                throw new IllegalStateException("No output stream");
            }
            M3uWriter.write(playlist, output);
            output.close();
            PlaylistIndex.register(this, playlist.displayName(this), PlaylistIndex.SOURCE_APP_PRIVATE);
            Toast.makeText(this, R.string.playlist_save_as, Toast.LENGTH_SHORT).show();
            if (getIntent().getBooleanExtra(EXTRA_SAVE_QUEUE, false)) {
                finish();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.playlist_open_error, Toast.LENGTH_LONG).show();
        }
    }

    private void playCurrent() {
        saveCurrentPlaylist();
        final Intent intent = new Intent(this, PlayerActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(editingUri);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FOLDER_ADD && resultCode == RESULT_OK && data != null) {
            final Uri tree = data.getData();
            if (tree == null) {
                return;
            }
            try {
                getContentResolver().takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            final DocumentFile root = DocumentFile.fromTreeUri(this, tree);
            if (root == null) {
                return;
            }
            final List<Uri> videos = SubtitleUtils.listVideosInDirectory(root);
            for (final Uri video : videos) {
                final String title = video.getLastPathSegment();
                editingEntries.add(new M3uEntry(video.toString(), video, title, -1));
            }
            if (entryAdapter != null) {
                entryAdapter.notifyDataSetChanged();
            }
            if (editingEntries.size() > PlaylistPlaybackState.softItemLimit()) {
                Toast.makeText(this,
                        getString(R.string.playlist_size_warning, editingEntries.size()),
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_COPY && resultCode == RESULT_OK && data != null) {
            final Uri dest = data.getData();
            if (dest == null) {
                return;
            }
            try {
                final M3uPlaylist playlist = new M3uPlaylist(dest, editingEntries);
                try (OutputStream output = getContentResolver().openOutputStream(dest)) {
                    M3uWriter.write(playlist, output);
                }
                PlaylistIndex.register(this, playlist.displayName(this), PlaylistIndex.SOURCE_EXTERNAL);
                Toast.makeText(this, R.string.playlist_save_as, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.playlist_open_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void confirmDelete(final PlaylistIndex.Entry entry) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.playlist_delete_confirm, entry.title))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    if (ContentResolver.SCHEME_FILE.equals(entry.uri.getScheme())) {
                        final File file = new File(entry.uri.getPath());
                        file.delete();
                    }
                    PlaylistIndex.unregister(this, entry.uri);
                    showPlaylistList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptRename(final PlaylistIndex.Entry entry) {
        final EditText input = new EditText(this);
        input.setText(entry.title);
        new AlertDialog.Builder(this)
                .setTitle(R.string.playlist_name_prompt)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    final String name = PlaylistIndex.sanitizeFileName(input.getText().toString());
                    if (ContentResolver.SCHEME_FILE.equals(entry.uri.getScheme())) {
                        final File oldFile = new File(entry.uri.getPath());
                        final File newFile = new File(oldFile.getParentFile(), name);
                        if (oldFile.renameTo(newFile)) {
                            PlaylistIndex.unregister(this, entry.uri);
                            PlaylistIndex.register(this,
                                    stripM3u(name), PlaylistIndex.SOURCE_APP_PRIVATE);
                            showPlaylistList();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String stripM3u(final String name) {
        if (name.toLowerCase().endsWith(".m3u")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private final class ListAdapter extends RecyclerView.Adapter<ListAdapter.Holder> {
        private final List<PlaylistIndex.Entry> items;

        ListAdapter(final List<PlaylistIndex.Entry> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_list, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final PlaylistIndex.Entry entry = items.get(position);
            holder.title.setText(entry.title);
            final String access;
            switch (entry.accessStatus) {
                case PARTIAL:
                    access = getString(R.string.playlist_access_partial);
                    break;
                case NONE:
                    access = getString(R.string.playlist_access_none);
                    break;
                case FULL:
                default:
                    access = getString(R.string.playlist_access_full);
                    break;
            }
            holder.meta.setText(getString(R.string.playlist) + " · " + entry.itemCount + " · " + access);
            holder.itemView.setOnClickListener(v -> openEditor(entry.uri));
            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(PlaylistEditorActivity.this)
                        .setItems(new CharSequence[]{
                                getString(R.string.playlist_name_prompt),
                                getString(R.string.playlist_save_as),
                                getString(android.R.string.cancel)
                        }, (dialog, which) -> {
                            if (which == 0) {
                                promptRename(entry);
                            } else if (which == 1) {
                                editingUri = entry.uri;
                                editingEntries.clear();
                                try {
                                    editingEntries.addAll(M3uReader.read(
                                            PlaylistEditorActivity.this, entry.uri).entries);
                                } catch (Exception ignored) {
                                }
                                final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                intent.setType("audio/x-mpegurl");
                                intent.putExtra(Intent.EXTRA_TITLE, entry.title + ".m3u");
                                startActivityForResult(intent, REQUEST_COPY);
                            }
                        })
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView meta;

            Holder(final View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.playlist_row_title);
                meta = itemView.findViewById(R.id.playlist_row_meta);
            }
        }
    }

    private final class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_entry, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final M3uEntry entry = editingEntries.get(position);
            holder.title.setText(entry.title);
            holder.uri.setText(entry.uri != null ? entry.uri.toString() : entry.uriString);
            holder.remove.setOnClickListener(v -> {
                final int pos = holder.getBindingAdapterPosition();
                if (pos >= 0 && pos < editingEntries.size()) {
                    editingEntries.remove(pos);
                    notifyItemRemoved(pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return editingEntries.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView uri;
            final View remove;

            Holder(final View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.entry_title);
                uri = itemView.findViewById(R.id.entry_uri);
                remove = itemView.findViewById(R.id.entry_remove);
            }
        }
    }
}
