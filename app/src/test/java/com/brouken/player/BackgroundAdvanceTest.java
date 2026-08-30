package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackgroundAdvanceTest {

    @Test
    public void playlistAlwaysStepsPastEnded() {
        assertTrue(BackgroundAdvance.shouldAdvance(false, true, false, false));
        assertTrue(BackgroundAdvance.shouldAdvance(false, true, false, true));
        assertTrue(BackgroundAdvance.usePlaylistNext(true));
    }

    @Test
    public void nextFileUriOnlyWhileBackgroundAudio() {
        assertFalse(BackgroundAdvance.shouldAdvance(false, false, true, false));
        assertTrue(BackgroundAdvance.shouldAdvance(false, false, true, true));
        assertFalse(BackgroundAdvance.usePlaylistNext(false));
    }

    @Test
    public void sleepAtEndWins() {
        assertFalse(BackgroundAdvance.shouldAdvance(true, true, true, true));
    }

    @Test
    public void lastItemWithNothingQueuedStaysEnded() {
        assertFalse(BackgroundAdvance.shouldAdvance(false, false, false, true));
        assertFalse(BackgroundAdvance.shouldAdvance(false, false, false, false));
    }
}
