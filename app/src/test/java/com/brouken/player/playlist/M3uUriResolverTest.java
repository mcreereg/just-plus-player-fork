package com.brouken.player.playlist;

import android.net.Uri;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class M3uUriResolverTest {

    @Test
    public void resolvesRelativeUnderFileBase() {
        final File dir = new File("/tmp/show");
        final Uri base = Uri.fromFile(new File(dir, "list.m3u"));
        final Uri resolved = M3uUriResolver.resolve(null, base, "Season 1/ep01.mkv");
        assertEquals(Uri.fromFile(new File(dir, "Season 1/ep01.mkv")), resolved);
    }

    @Test
    public void relativizeFilePaths() {
        final Uri base = Uri.fromFile(new File("/tmp/show/list.m3u"));
        final Uri entry = Uri.fromFile(new File("/tmp/show/Season 1/ep01.mkv"));
        assertTrue(M3uUriResolver.canRelativize(base, entry));
        assertEquals("Season 1/ep01.mkv", M3uUriResolver.relativize(base, entry));
    }

    @Test
    public void absoluteNetworkPassthrough() {
        final Uri resolved = M3uUriResolver.resolve(null, null, "https://cdn.example/v.mp4");
        assertEquals("https://cdn.example/v.mp4", resolved.toString());
    }
}
