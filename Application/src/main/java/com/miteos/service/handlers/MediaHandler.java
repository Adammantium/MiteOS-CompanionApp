package com.miteos.service.handlers;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.util.Log;

import androidx.annotation.NonNull;

import com.miteos.activity.MainActivity;
import com.miteos.service.MainService;

public class MediaHandler {

    private static MediaSessionManager m;
    private static ComponentName component;

    public static class MediaInfo {
        public String title;
        public String artist;
        public String album;
        public Bitmap art;

        public MediaInfo(MediaController controller) {
            title = controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE);
            album = controller.getMetadata().getString(MediaMetadata.METADATA_KEY_ALBUM);
            artist = controller.getMetadata().getString(MediaMetadata.METADATA_KEY_ARTIST);
            art = controller.getMetadata().getBitmap(MediaMetadata.METADATA_KEY_ART);
        }

        @NonNull
        @Override
        public String toString() {
            return title + " " + album + " " + artist;
        }
    }
    public static MediaInfo getPlaybackInfos() {
        if(m == null) init();

        java.util.List<android.media.session.MediaController> sessions = m.getActiveSessions(component);

        if(sessions.isEmpty()) return null;

        for(MediaController session : sessions) {
            if(session.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                return new MediaInfo(session);
            }
        }
        return new MediaInfo(sessions.get(0));
    }

    public static void init() {
        m = (MediaSessionManager) MainService.instance.getSystemService(Context.MEDIA_SESSION_SERVICE);
        component = new ComponentName(MainService.instance, MediaHandler.class);
    }
}
