package com.miteos.service.handlers;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.miteos.service.MainService;
import com.miteos.service.data.CalendarEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class CalendarHandler {
    private static final String TAG = "CalendarHandler";
    private static final String CALENDAR_SYNC_ITEMS = "calendar_sync_items";

    public static JSONObject getUpcomingEvents() {
        try {
            Context context = MainService.instance;
            if (context == null) {
                Log.e(TAG, "Context is null");
                return null;
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Calendar permission not granted");
                return null;
            }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> selectedCalendars = prefs.getStringSet(CALENDAR_SYNC_ITEMS, new HashSet<>());

            if (selectedCalendars.isEmpty()) {
                Log.d(TAG, "No calendars selected");
                return createEmptyResponse();
            }

            ArrayList<CalendarEvent> events = new ArrayList<>();
            ContentResolver contentResolver = context.getContentResolver();

            // Get events from today onwards
            Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
            long now = System.currentTimeMillis();
            ContentUris.appendId(builder, now);
            ContentUris.appendId(builder, now + (30L * 24L * 60L * 60L * 1000L)); // Next 30 days

            String selection = CalendarContract.Instances.CALENDAR_ID + " IN (" + 
                String.join(",", selectedCalendars) + ")";

            String[] projection = new String[]{
                CalendarContract.Instances._ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME
            };

            Cursor cursor = contentResolver.query(
                builder.build(),
                projection,
                selection,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    CalendarEvent event = new CalendarEvent();
                    event.id = cursor.getLong(0);
                    event.title = cursor.getString(1);
                    event.description = cursor.getString(2);
                    event.startTime = cursor.getLong(3);
                    event.endTime = cursor.getLong(4);
                    event.location = cursor.getString(5);
                    event.allDay = cursor.getInt(6) == 1;
                    event.calendarId = cursor.getString(7);
                    event.calendarName = cursor.getString(8);
                    events.add(event);
                }
                cursor.close();
            }

            return createResponse(events);
        } catch (Exception e) {
            Log.e(TAG, "Error getting calendar events", e);
            return null;
        }
    }

    private static JSONObject createResponse(ArrayList<CalendarEvent> events) throws JSONException {
        JSONObject response = new JSONObject();
        JSONArray eventsArray = new JSONArray();

        for (CalendarEvent event : events) {
            JSONObject eventObj = new JSONObject();
            eventObj.put("id", event.id);
            eventObj.put("title", event.title);
            eventObj.put("description", event.description);
            eventObj.put("startTime", event.startTime / 1000);
            eventObj.put("endTime", event.endTime / 1000);
            eventObj.put("location", event.location);
            eventObj.put("allDay", event.allDay);
            eventObj.put("calendarId", event.calendarId);
            eventObj.put("calendarName", event.calendarName);
            eventsArray.put(eventObj);
        }

        response.put("events", eventsArray);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    private static JSONObject createEmptyResponse() throws JSONException {
        JSONObject response = new JSONObject();
        response.put("events", new JSONArray());
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
} 