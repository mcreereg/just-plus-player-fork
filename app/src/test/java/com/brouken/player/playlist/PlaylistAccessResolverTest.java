package com.brouken.player.playlist;

import android.net.Uri;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlaylistAccessResolverTest {

    @Test
    public void networkEntriesSkippedInGrouping() {
        final M3uEntry net = new M3uEntry("https://a/x.mp4", Uri.parse("https://a/x.mp4"), "A", -1);
        final M3uEntry local = new M3uEntry("content://a/1", Uri.parse("content://a/1"), "B", -1);
        final Map<String, List<M3uEntry>> groups = PlaylistAccessResolver.groupMissingByParent(
                Arrays.asList(local));
        assertEquals(1, groups.size());
        assertTrue(PlaylistAccessResolver.isNetworkEntry(net));
    }
}
