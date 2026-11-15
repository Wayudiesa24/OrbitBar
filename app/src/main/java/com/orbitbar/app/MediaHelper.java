package com.orbitbar.app;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import java.util.List;

final class MediaHelper {
    static MediaController current(Context c) {
        try {
            MediaSessionManager m = (MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (m == null) return null;
            List<MediaController> controllers = m.getActiveSessions(new ComponentName(c, MediaListenerService.class));
            MediaController fallback = null;
            for (MediaController controller : controllers) {
                if (fallback == null) fallback = controller;
                PlaybackState p = controller.getPlaybackState();
                if (p != null && p.getState() == PlaybackState.STATE_PLAYING) return controller;
            }
            return fallback;
        } catch (SecurityException e) { return null; }
    }
    static String label(MediaController m) {
        if (m == null) return "No active media session";
        MediaMetadata metadata = m.getMetadata();
        if (metadata == null) return "Media controls available";
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        return (title == null ? "Unknown track" : title) + (artist == null ? "" : " · " + artist);
    }
    static boolean isPlaying(MediaController m) {
        return m != null && m.getPlaybackState() != null &&
                m.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
    }
    static void mediaAction(Context c, String command) {
        MediaController m = current(c); if (m == null) return;
        try {
            switch (command) {
                case "prev": m.getTransportControls().skipToPrevious(); break;
                case "next": m.getTransportControls().skipToNext(); break;
                case "play":
                    if (isPlaying(m)) m.getTransportControls().pause();
                    else m.getTransportControls().play();
                    break;
            }
        } catch (Exception ignored) { }
    }
    private MediaHelper() {}
}
