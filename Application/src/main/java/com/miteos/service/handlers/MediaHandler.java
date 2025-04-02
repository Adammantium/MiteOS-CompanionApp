package com.miteos.service.handlers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.preference.PreferenceManager;

import com.miteos.activity.MainActivity;
import com.miteos.func.SteinbergDithering;import com.miteos.service.MainService;

import java.io.ByteArrayOutputStream;import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;

public class MediaHandler {

    private static MediaSessionManager m;
    private static ComponentName component;

    public static class MediaInfo {
        public String title;
        public String artist;
        public String album;
        public String art;
        public long position;
        public long duration;
        public long timestamp;
        public boolean playing;

        public MediaInfo(MediaController controller) {
            title = controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE);
            album = controller.getMetadata().getString(MediaMetadata.METADATA_KEY_ALBUM);
            artist = controller.getMetadata().getString(MediaMetadata.METADATA_KEY_ARTIST);
            position = controller.getPlaybackState().getPosition() / 1000;
            duration = controller.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION) / 1000;
            timestamp = System.currentTimeMillis() / 1000;
            playing = controller.getPlaybackState().isActive();

            art = MediaHandler.GenerateImage(controller.getMetadata().getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART));
        }

        @NonNull
        @Override
        public String toString() {
            return title + " " + album + " " + artist;
        }
    }
    public static MediaController getPlaybackSession() {
        if(m == null) init();

        java.util.List<android.media.session.MediaController> sessions = m.getActiveSessions(component);

        if(sessions.isEmpty()) return null;

        for(MediaController session : sessions) {
            if(session == null || session.getPlaybackState() == null || session.getPlaybackState().getState() == PlaybackState.STATE_NONE) continue;

            if(session.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                return session;
            }
        }
        return sessions.get(0);
    }
    public static MediaInfo getPlaybackInfos() {
        if(m == null) init();

        MediaController session = getPlaybackSession();

        if(session == null) return null;
        if(session.getMetadata() == null) return null;

        return new MediaInfo(session);
    }

    public static void toggle() {
        if(m == null) init();

        MediaController session = getPlaybackSession();

        if(session == null) return;

        if(session.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
            session.getTransportControls().pause();
        }else{
            session.getTransportControls().play();
        }
    }

    public static void next() {
        if(m == null) init();

        MediaController session = getPlaybackSession();

        if(session == null) return;

        session.getTransportControls().skipToNext();
    }

    public static void previous() {
        if(m == null) init();

        MediaController session = getPlaybackSession();

        if(session == null) return;

        session.getTransportControls().skipToPrevious();
    }




    public static void init() {
        m = (MediaSessionManager) MainService.instance.getSystemService(Context.MEDIA_SESSION_SERVICE);
        component = new ComponentName(MainService.instance, MediaHandler.class);
    }

    private static boolean convertPixel(Color clr) {
        if((255 * 3 / 2) < (clr.red() + clr.green() + clr.blue())) return true;

        return false;
    }


    public static String GenerateImage(Bitmap src) {
        if(src == null) return "";

        int size = 96;


        Bitmap bmp = Bitmap.createScaledBitmap(src, size, size, false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainService.instance);
        boolean shouldDither = prefs.getBoolean("dither_album_cover", false);
        if(shouldDither) {
            bmp = SteinbergDithering.floydSteinbergDithering(bmp);
        }

        /*ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream .toByteArray();
        Log.e("1", Base64.getEncoder().encodeToString(byteArray));*/

        /*byteArrayOutputStream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byteArray = byteArrayOutputStream .toByteArray();
        Log.e("2", Base64.getEncoder().encodeToString(byteArray));*/

        boolean bits[] = new boolean[size * size];

        //String test = "";
        for (int y = 0; y < bmp.getHeight(); y++) {
            for (int x = 0; x < bmp.getWidth(); x++) {
                Color clr = bmp.getColor(x, y);

                bits[x + y * size] = clr.red() > 0.5;
            }
        }

        /*
        String test = "";
        for (int y = 0; y < bmp.getHeight(); y++) {
            for (int x = 0; x < bmp.getWidth(); x++) {
                if(bits[x + y * size]) test += "1";
                else test += "0";
            }
        }
        */

        /*
        NotificationChannel channel = new NotificationChannel("com.example.TEST_CHANNEL", "Test-Channel",  NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("PennSkanvTic channel for foreground service notification");

        NotificationManager notificationManager = (NotificationManager) MainService.instance.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(MainService.instance, "com.example.TEST_CHANNEL")
                .setContentTitle("Test")
                .setContentText("Test")
                .setSmallIcon(IconCompat.createWithBitmap(bmp))
                .setLargeIcon(bmp)
                .build();
        notificationManager.notify(1, notification);
        */

        return Base64.getEncoder().encodeToString(toByteArray(bits));
    }

    static boolean[] toBooleanArray(byte[] bytes) {
        BitSet bits = BitSet.valueOf(bytes);
        boolean[] bools = new boolean[bytes.length * 8];
        for (int i = bits.nextSetBit(0); i != -1; i = bits.nextSetBit(i+1)) {
            bools[i] = true;
        }
        return bools;
    }

    static byte[] toByteArray(boolean[] bools) {
        byte[] result = new byte[(int) Math.ceil(bools.length / 8.0)];

        int i = 0;
        while(i < result.length) {
            byte b = 0;
            for(int bi = 0; bi < 8; bi++) {
                b <<= 1;
                if(bools.length > i * 8 + bi && bools[i * 8 + bi]) b |= 1;
            }
            result[i] = b;
            i++;
        }

        return result;
    }
}
