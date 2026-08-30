package com.brouken.player;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * Keeps a {@link MediaSession} in a media-playback foreground service so audio can outlive the
 * player screen: Home, an app switch, or the power button all stop {@link PlayerActivity}, and
 * without a foreground service the process is fair game the moment the screen is off.
 *
 * <p>The {@link Player} itself stays on the activity — building it is a large, stateful job, and
 * moving it here would duplicate that. This service only advertises the session and hosts the
 * notification. {@link PlayerActivity} decides whether to pause on {@code onStop}; when it does not,
 * playback continues through the session this service holds.
 *
 * <p>One session at a time. The activity attaches after each rebuild and detaches before
 * {@link Player#release()}, so the session is never left pointing at a dead player.
 * {@link #attach} is also called after a playlist step from {@code STATE_ENDED}, because the
 * idle-player notification policy can stop this service in the gap between items.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackService extends MediaSessionService {

    private static final String SESSION_ID = "justplus";

    private static PlaybackService instance;
    private static Player pendingPlayer;
    private static PendingIntent pendingSessionActivity;

    private MediaSession mediaSession;

    /**
     * Point the session at {@code player} and start this service if it is not already up. Safe to
     * call again with the same player; a rebuild passes the new instance after detaching the old one.
     */
    public static void attach(Context context, Player player) {
        if (player == null || !player.canAdvertiseSession()) {
            return;
        }
        final Context app = context.getApplicationContext();
        pendingPlayer = player;
        pendingSessionActivity = sessionActivity(app);
        if (instance != null) {
            instance.bindPlayer(player);
            return;
        }
        final Intent intent = new Intent(app, PlaybackService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (IllegalStateException e) {
            // Starting a service from a stopped activity can be refused. The instance path
            // above already covered a service that is up; nothing to advertise otherwise.
            e.printStackTrace();
        }
    }

    /**
     * Drop the session for {@code player} without releasing the player. The activity still owns
     * teardown; releasing here would race a rebuild that is about to attach the next instance.
     */
    public static void detach(Player player) {
        if (pendingPlayer == player) {
            pendingPlayer = null;
        }
        if (instance != null) {
            instance.unbind(player);
        }
    }

    private static PendingIntent sessionActivity(Context context) {
        final Intent launch = new Intent(context, PlayerActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 0, launch, flags);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER);
        final DefaultMediaNotificationProvider notificationProvider =
                new DefaultMediaNotificationProvider.Builder(this)
                        .setChannelName(R.string.playback_channel_name)
                        .build();
        notificationProvider.setSmallIcon(R.drawable.ic_play_triangle);
        setMediaNotificationProvider(notificationProvider);
        if (pendingPlayer != null) {
            bindPlayer(pendingPlayer);
        }
    }

    @Override
    public void onDestroy() {
        unbind(mediaSession != null ? mediaSession.getPlayer() : null);
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        // Swiping the card away is leaving for good, not a trip to the background. Pause so the
        // activity's onDestroy (which still owns the player) does not tear down a playing session,
        // and let MediaSessionService drop the foreground state.
        pauseAllPlayersAndStopSelf();
    }

    private void bindPlayer(Player player) {
        if (player == null || !player.canAdvertiseSession()) {
            return;
        }
        if (mediaSession != null && mediaSession.getPlayer() == player) {
            return;
        }
        if (mediaSession != null) {
            removeSession(mediaSession);
            mediaSession.release();
            mediaSession = null;
        }
        try {
            final MediaSession.Builder builder = new MediaSession.Builder(this, player)
                    .setId(SESSION_ID);
            if (pendingSessionActivity != null) {
                builder.setSessionActivity(pendingSessionActivity);
            }
            mediaSession = builder.build();
            addSession(mediaSession);
        } catch (IllegalStateException e) {
            // Player already advertised a session (a rebuild that did not detach) or the process
            // already has one with this id. Either way there is nothing this service can add.
            e.printStackTrace();
        }
    }

    private void unbind(Player player) {
        if (mediaSession == null) {
            return;
        }
        if (player != null && mediaSession.getPlayer() != player) {
            return;
        }
        removeSession(mediaSession);
        mediaSession.release();
        mediaSession = null;
    }
}
