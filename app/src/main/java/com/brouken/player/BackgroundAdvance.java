package com.brouken.player;

/**
 * Whether a natural end should start another file instead of sitting finished.
 *
 * <p>ExoPlayer usually steps a playlist itself. Disabling the video renderer for background
 * audio, or a text renderer that never reports ended, can land on {@code STATE_ENDED} with a
 * next item still queued. A loose next-file URI (the leftover "next" button, no folder
 * playlist) is only consumed while background audio is meant to keep going — foreground still
 * shows the button.
 */
final class BackgroundAdvance {

    private BackgroundAdvance() {}

    static boolean shouldAdvance(boolean sleepAtEnd, boolean hasNextItem,
                                 boolean hasNextUri, boolean backgroundSession) {
        if (sleepAtEnd) {
            return false;
        }
        return hasNextItem || (hasNextUri && backgroundSession);
    }

    static boolean usePlaylistNext(boolean hasNextItem) {
        return hasNextItem;
    }
}
