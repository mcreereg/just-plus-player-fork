# Playlist support — implementation plan

Status: **ready for implementation** after user says **GO**.  
Design reference: `docs/playlist-support-design.md`.

---

## Phase 1 — Core M3U library (no UI)

**Goal:** Parse/write M3U; unit tests green.

### New files

```
app/src/main/java/com/brouken/player/playlist/
  M3uEntry.java
  M3uPlaylist.java
  M3uReader.java
  M3uWriter.java
  M3uUriResolver.java
```

### Tasks

1. **`M3uEntry`** — fields: `String uri`, `String title`, `int durationSec` (default -1).
2. **`M3uPlaylist`** — `String title`, `List<M3uEntry> entries`, `Uri sourceUri` optional.
3. **`M3uReader`**
   - `read(InputStream, Uri baseUri)` → `M3uPlaylist`
   - Handle `#EXTM3U`, `#EXTINF`, `#PLAYLIST:`, comments, blank lines
   - Simple M3U (URI-only lines) fallback
4. **`M3uWriter`**
   - `write(M3uPlaylist, OutputStream)` UTF-8
   - Relative path when `M3uUriResolver.canRelativize(base, entry)`
5. **`M3uUriResolver`**
   - `resolve(Uri base, String entry)`
   - `relativize(Uri base, Uri entry)` for writer
6. **Tests** — `app/src/test/java/com/brouken/player/playlist/M3uRoundTripTest.java`, `M3uUriResolverTest.java`

### Acceptance

- Round-trip sample extended M3U from test resources.
- Relative path `Season 1/ep01.mkv` resolves under `file://` base.

---

## Phase 2 — State and index

**Goal:** Persist playlist index; maintain registry.

### New files

```
app/src/main/java/com/brouken/player/playlist/
  PlaylistPlaybackState.java
  PlaylistIndex.java
```

### Tasks

1. **`PlaylistPlaybackState`**
   - Load/save `playlist_state` JSON in app files dir
   - `getIndex(Uri)`, `setIndex(Uri, int)`, `getLastPlaylist()`, `setLastPlaylist(Uri, int)`
   - Hook clamp when queue shrinks
2. **`PlaylistIndex`**
   - `register(Uri, title, source)`
   - `unregister(Uri)`
   - `loadAll(Context)` → merge: app-private dir scan + prefs registry + scoped scan
   - `scanAppPrivate(Context)`, `scanScoped(Context, Uri scopeUri)`
3. **Tests** — `PlaylistPlaybackStateTest.java`

### `Prefs.java` touches

- None required for positions (reuse existing API).
- Optional: helper `getPosition(Uri)` already exists as `getPosition()` using `mediaUri` — use `positions.get(uri.toString())` directly or add overload.

### Acceptance

- Index survives process restart in instrumented test.

---

## Phase 3 — Access resolver

**Goal:** Batch SAF prompts; skipped list.

### New file

```
app/src/main/java/com/brouken/player/playlist/PlaylistAccessResolver.java
```

### Tasks

1. `canRead(Context, Uri)` for content/file schemes.
2. `groupByParent(List<M3uEntry>)` → `Map<Uri, List<M3uEntry>>` (parent document URI).
3. `ensureAccess(PlayerActivity, M3uPlaylist, Callback)` async-friendly:
   - Returns `AccessResult { List<M3uEntry> accessible, List<M3uEntry> skipped }`
   - Drives `startActivityForResult` chains via activity delegate methods
4. Add request codes on `PlayerActivity`:
   - `REQUEST_PLAYLIST_ACCESS_TREE = 30`
   - `REQUEST_PLAYLIST_ACCESS_FILES = 31`
5. **Tests** — unit test grouping only (`PlaylistAccessResolverTest.java`).

### Acceptance

- Mocked grouping: 5 files in 2 folders → at most 2 prompt rounds.

---

## Phase 4 — Playback integration

**Goal:** Open `.m3u` and play queue.

### `PlayerActivity.java` changes

1. Fields: `filePlaylist`, `filePlaylistUri`, `filePlaylistTitle`.
2. `isPlaylistFile(Uri, String mime)` — extension + MIME + optional sniff.
3. `openPlaylistFile(Uri)` — parse → access → build queue → play.
4. `buildFilePlaylistQueue(List<M3uEntry>)` — populate `apiMediaItems`, `apiPlaylistPositions` from `Prefs` per URI, set index from `PlaylistPlaybackState`.
5. `resetApiAccess()` — clear file playlist fields.
6. `openFile()` — add M3U MIME types to SAF intent.
7. `onActivityResult` — handle access request codes; route playlist pick.
8. `saveLastSession()` / `restoreLastSession()` — include `filePlaylist*` fields.
9. `onMediaItemTransition` listener — `PlaylistPlaybackState.setIndex`.
10. Reuse existing periodic session tick for index persist.
11. `showSkippedItemsToast(List<M3uEntry>)`.

### `LastSession.java` changes

- Add `filePlaylist`, `filePlaylistUri`, `filePlaylistTitle` JSON keys.

### `AndroidManifest.xml`

- `.m3u` path patterns + MIME types on `PlayerActivity` VIEW filter.

### `Utils.java`

- `supportedMimeTypesPlaylist` array; merge into open intent when needed.

### Acceptance

- Open test `.m3u` from assets; plays first accessible item.
- Folder auto-next still works for single-file open (regression).
- API `video_list` intent unchanged (regression).

---

## Phase 5 — Player UI

**Goal:** Panel title; save as playlist.

### Changes

1. **`showPlaylistDialog()`** — if `filePlaylist`, subtitle/header shows `filePlaylistTitle`.
2. **Save as playlist** — menu item in playlist panel header or More menu:
   - `saveQueueAsPlaylist()` → build `M3uPlaylist` from `apiMediaItems` → launch editor or quick-save dialog.
3. **`LastSessionTest.java`** — extend for new fields.

### Strings

- Add keys listed in design doc to `values/strings.xml`.

### Acceptance

- Panel shows "Playlist — My Show".
- Save from folder queue creates `.m3u` in app-private dir.

---

## Phase 6 — Playlist editor activity

**Goal:** Full CRUD UI (phone/tablet).

### New files

```
app/src/main/java/com/brouken/player/playlist/PlaylistEditorActivity.java
app/src/main/res/layout/activity_playlist_editor.xml
app/src/main/res/layout/item_playlist_list.xml
app/src/main/res/layout/item_playlist_entry.xml
app/src/main/res/menu/menu_playlist_editor.xml
```

### `AndroidManifest.xml`

```xml
<activity android:name=".playlist.PlaylistEditorActivity"
    android:exported="false"
    android:label="@string/playlist_editor" />
```

### `activity_player.xml` + `EmptyState.java`

- Insert `empty_state_playlists` pill between `empty_state_link` and `empty_state_room`.
- Icon: `ic_playlist_24dp` (existing).
- Click → `PlaylistEditorActivity`.
- `View.GONE` when `PlayerActivity.isTvBox`.

### Editor tasks

1. **List fragment/screen** — `RecyclerView` of playlists from `PlaylistIndex.loadAll()`.
2. **Access badge** on row.
3. **New** — name dialog → empty file → edit screen.
4. **Edit screen** — `ItemTouchHelper` drag, delete row, add from folder.
5. **Add from folder** — `ACTION_OPEN_DOCUMENT_TREE` → list videos → append.
6. **Rename** — filename + title dialog; `DocumentsContract.renameDocument` or rewrite.
7. **Copy** — `ACTION_CREATE_DOCUMENT` save-as.
8. **Delete** — confirm, delete file, unregister.
9. **Play** — `startActivity` to `PlayerActivity` with playlist URI extra `com.brouken.player.PLAYLIST_URI` (new constant) or `ACTION_VIEW` on file URI.

### Request codes

- `REQUEST_PLAYLIST_FOLDER_ADD = 32`
- `REQUEST_PLAYLIST_COPY = 33`

### Acceptance

- Create empty → add folder → 3 items → play works.
- Partial-access playlist shows badge.
- TV: editor button hidden.

---

## Phase 7 — Polish and README

1. **README.md** — short section on M3U playlists and editor.
2. **500 item warning** — dialog in `openPlaylistFile` and editor append.
3. **Error handling** — corrupt M3U → snackbar + stay on menu.
4. Manual QA matrix:

| Case | Expected |
|------|----------|
| Open `.m3u` from picker | Queue plays |
| Open `.m3u` from Files app | External intent |
| Single video in folder | Folder auto-next still on |
| LAMPA `video_list` | API queue, no file flag |
| Recents swipe | Resume index + per-URI position |
| Editor on TV | Button hidden |
| Playlist edited on disk while playing | Ignored until re-open |

---

## File change summary

| Path | Action |
|------|--------|
| `playlist/*.java` (8 classes) | **New** |
| `PlaylistEditorActivity.java` + layouts/menus | **New** |
| `PlayerActivity.java` | Modify |
| `LastSession.java` | Modify |
| `EmptyState.java` | Modify |
| `activity_player.xml` | Modify |
| `AndroidManifest.xml` | Modify |
| `Utils.java` | Modify |
| `values/strings.xml` | Modify |
| `LastSessionTest.java` | Modify |
| `playlist/*Test.java` | **New** |
| `README.md` | Modify |
| `docs/playlist-support-*.md` | Done |

---

## Estimated dependency order

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5
                              ↘
                               Phase 6 (can start after Phase 2 in parallel, needs Phase 4 for Play)
                               → Phase 7
```

---

## Implementation notes for `PlayerActivity` size

`PlayerActivity.java` is ~14k lines. Prefer:

- Keep M3U logic in `playlist/` package.
- `PlayerActivity` only: thin orchestration (`openPlaylistFile`, result handlers, flags).
- Avoid further inflating `parseApiPlaylist`-sized methods inside activity.

---

## Open questions (none blocking — resolved)

All decisions captured in `playlist-support-design.md` decisions log.

**Await user `GO` to begin Phase 1.**
