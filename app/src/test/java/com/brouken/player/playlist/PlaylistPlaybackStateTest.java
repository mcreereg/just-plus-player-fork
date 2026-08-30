package com.brouken.player.playlist;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaylistPlaybackStateTest {

    @Test
    public void softLimitIs100() {
        assertEquals(100, PlaylistPlaybackState.softItemLimit());
    }
}
