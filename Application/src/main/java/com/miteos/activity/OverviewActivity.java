package com.miteos.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class OverviewActivity extends AppCompatActivity {

    public static OverviewActivity current;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        current = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private static final String HASS_LIST_ITEMS = "hass_list_items";
        private static final String HASS_LIST_ITEM_NAME = "name";
        private static final String HASS_LIST_ITEM_KEY = "key";
        private static final String TOTP_LIST_ITEMS = "totp_list_items";
        private static final String TOTP_LIST_ITEM_NAME = "name";
        private static final String TOTP_LIST_ITEM_TOKEN = "token";
        private static final String CALENDAR_SYNC_ITEMS = "calendar_sync_items";

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference button = findPreference("pairWatch");
            if(button != null) {
                button.setOnPreferenceClickListener(preference -> {
                    Intent myIntent = new Intent(OverviewActivity.current, MainActivity.class);
                    startActivity(myIntent);
                    return true;
                });
            }

            // Handle Home Assistant URL preference
            setupHassUrlPreference();

            // Handle Home Assistant API Token preference
            setupHassTokenPreference();

            setupOpenWeatherUrlPreference();
            setupOpenWeatherTempPreference();
            setupOpenWeatherLonPreference();
            setupOpenWeatherLatPreference();
            setupOpenWeatherCityPreference();

            // Handle Home Assistant List Management
            setupHassListManagement();

            // Handle TOTP List Management
            setupTotpListManagement();

            // Handle Calendar Sync Management
            setupCalendarManagement();
        }

        private void setupHassUrlPreference() {
            Preference hassUrlPref = findPreference("hassUrl");
            if (hassUrlPref != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                String currentUrl = prefs.getString("hassUrl", "http://127.0.0.1");
                hassUrlPref.setSummary(currentUrl);

                hassUrlPref.setOnPreferenceClickListener(preference -> {
                    try {
                        Context context = getContext();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.hass_url);

                        final EditText input = new EditText(context);
                        input.setInputType(InputType.TYPE_CLASS_TEXT);
                        input.setText(currentUrl);
                        input.setSelectAllOnFocus(true);
                        
                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(50, 20, 50, 0);
                        layout.addView(input);
                        
                        builder.setView(layout);

                        builder.setPositiveButton("OK", (dialog, which) -> {
                            String url = input.getText().toString().trim();
                            if (!url.isEmpty()) {
                                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                    url = "http://" + url;
                                }
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("hassUrl", url);
                                editor.apply();
                                hassUrlPref.setSummary(url);
                                Toast.makeText(context, "URL updated", Toast.LENGTH_SHORT).show();
                            }
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                        builder.show();
                        return true;
                    } catch (Exception e) {
                        Log.e("Preferences", "Error showing URL dialog", e);
                        return false;
                    }
                });
            }
        }

        private void setupHassTokenPreference() {
            Preference hassTokenPref = findPreference("hass_token");
            if (hassTokenPref != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                String currentToken = prefs.getString("hass_token", "");
                hassTokenPref.setSummary(currentToken.isEmpty() ? "Not set" : "********");

                hassTokenPref.setOnPreferenceClickListener(preference -> {
                    try {
                        Context context = getContext();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.hass_token);

                        final EditText input = new EditText(context);
                        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        input.setText(currentToken);
                        input.setSelectAllOnFocus(true);
                        
                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(50, 20, 50, 0);
                        layout.addView(input);
                        
                        builder.setView(layout);

                        builder.setPositiveButton("OK", (dialog, which) -> {
                            String token = input.getText().toString().trim();
                            if (!token.isEmpty()) {
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("hass_token", token);
                                editor.apply();
                                hassTokenPref.setSummary("********");
                                Toast.makeText(context, "API Token updated", Toast.LENGTH_SHORT).show();
                            }
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                        builder.show();
                        return true;
                    } catch (Exception e) {
                        Log.e("Preferences", "Error showing token dialog", e);
                        return false;
                    }
                });
            }
        }

        private void setupHassListManagement() {
            Preference hassListPref = findPreference("hass_list_manage");
            if (hassListPref != null) {
                hassListPref.setOnPreferenceClickListener(preference -> {
                    try {
                        Context context = getContext();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.hass_list_add_dialog_title);

                        // Create layout for the dialog
                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(50, 20, 50, 0);

                        // Add name input
                        final EditText nameInput = new EditText(context);
                        nameInput.setHint(R.string.hass_list_name);
                        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
                        layout.addView(nameInput);

                        // Add some spacing
                        View spacing = new View(context);
                        spacing.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 20));
                        layout.addView(spacing);

                        // Add key input
                        final EditText keyInput = new EditText(context);
                        keyInput.setHint(R.string.hass_list_key);
                        keyInput.setInputType(InputType.TYPE_CLASS_TEXT);
                        layout.addView(keyInput);

                        builder.setView(layout);

                        builder.setPositiveButton("Add", (dialog, which) -> {
                            String name = nameInput.getText().toString().trim();
                            String key = keyInput.getText().toString().trim();

                            if (!name.isEmpty() && !key.isEmpty()) {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                                String listItemsJson = prefs.getString(HASS_LIST_ITEMS, "[]");
                                try {
                                    JSONArray listItems = new JSONArray(listItemsJson);
                                    JSONObject newItem = new JSONObject();
                                    newItem.put(HASS_LIST_ITEM_NAME, name);
                                    newItem.put(HASS_LIST_ITEM_KEY, key);
                                    listItems.put(newItem);

                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.putString(HASS_LIST_ITEMS, listItems.toString());
                                    editor.apply();

                                    Toast.makeText(context, "Item added successfully", Toast.LENGTH_SHORT).show();
                                    showListItems(context);
                                } catch (JSONException e) {
                                    Log.e("Preferences", "Error saving list item", e);
                                    Toast.makeText(context, "Error saving item", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(context, "Both name and key are required", Toast.LENGTH_SHORT).show();
                            }
                        });

                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                        // Add "View List" button
                        builder.setNeutralButton("View List", (dialog, which) -> {
                            showListItems(context);
                        });

                        builder.show();
                        return true;
                    } catch (Exception e) {
                        Log.e("Preferences", "Error showing list management dialog", e);
                        return false;
                    }
                });
            }
        }

        private void showListItems(Context context) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String listItemsJson = prefs.getString(HASS_LIST_ITEMS, "[]");
            try {
                JSONArray listItems = new JSONArray(listItemsJson);
                if (listItems.length() == 0) {
                    Toast.makeText(context, R.string.hass_list_empty, Toast.LENGTH_SHORT).show();
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Saved Entities");

                // Create list of items
                String[] items = new String[listItems.length()];
                for (int i = 0; i < listItems.length(); i++) {
                    JSONObject item = listItems.getJSONObject(i);
                    items[i] = item.getString(HASS_LIST_ITEM_NAME) + " (" + 
                              item.getString(HASS_LIST_ITEM_KEY) + ")";
                }

                builder.setItems(items, (dialog, which) -> {
                    // Show edit dialog when item is clicked
                    showEditDialog(context, listItems, which);
                });

                // Add delete option
                builder.setNeutralButton("Delete", (dialog, which) -> {
                    showDeleteDialog(context, items, listItems);
                });

                builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());

                builder.show();
            } catch (JSONException e) {
                Log.e("Preferences", "Error showing list items", e);
                Toast.makeText(context, "Error showing items", Toast.LENGTH_SHORT).show();
            }
        }

        private void showEditDialog(Context context, JSONArray listItems, int position) {
            try {
                JSONObject item = listItems.getJSONObject(position);
                String currentName = item.getString(HASS_LIST_ITEM_NAME);
                String currentKey = item.getString(HASS_LIST_ITEM_KEY);

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.hass_list_edit);

                // Create layout for the dialog
                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(50, 20, 50, 0);

                // Add name input
                final EditText nameInput = new EditText(context);
                nameInput.setHint(R.string.hass_list_name);
                nameInput.setText(currentName);
                nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
                layout.addView(nameInput);

                // Add some spacing
                View spacing = new View(context);
                spacing.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 20));
                layout.addView(spacing);

                // Add key input
                final EditText keyInput = new EditText(context);
                keyInput.setHint(R.string.hass_list_key);
                keyInput.setText(currentKey);
                keyInput.setInputType(InputType.TYPE_CLASS_TEXT);
                layout.addView(keyInput);

                builder.setView(layout);

                builder.setPositiveButton("Save", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String key = keyInput.getText().toString().trim();

                    if (!name.isEmpty() && !key.isEmpty()) {
                        try {
                            JSONObject updatedItem = new JSONObject();
                            updatedItem.put(HASS_LIST_ITEM_NAME, name);
                            updatedItem.put(HASS_LIST_ITEM_KEY, key);
                            listItems.put(position, updatedItem);

                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString(HASS_LIST_ITEMS, listItems.toString());
                            editor.apply();

                            Toast.makeText(context, "Entity updated successfully", Toast.LENGTH_SHORT).show();
                            showListItems(context);
                        } catch (JSONException e) {
                            Log.e("Preferences", "Error updating item", e);
                            Toast.makeText(context, "Error updating entity", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(context, "Both name and entity ID are required", Toast.LENGTH_SHORT).show();
                    }
                });

                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder.show();
            } catch (JSONException e) {
                Log.e("Preferences", "Error showing edit dialog", e);
                Toast.makeText(context, "Error editing entity", Toast.LENGTH_SHORT).show();
            }
        }

        private void showDeleteDialog(Context context, String[] items, JSONArray listItems) {
            AlertDialog.Builder deleteBuilder = new AlertDialog.Builder(context);
            deleteBuilder.setTitle("Select entity to delete");
            deleteBuilder.setItems(items, (dialog, which) -> {
                try {
                    listItems.remove(which);
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(HASS_LIST_ITEMS, listItems.toString());
                    editor.apply();
                    Toast.makeText(context, "Entity deleted", Toast.LENGTH_SHORT).show();
                    showListItems(context); // Refresh the list view
                } catch (Exception e) {
                    Log.e("Preferences", "Error deleting item", e);
                    Toast.makeText(context, "Error deleting entity", Toast.LENGTH_SHORT).show();
                }
            });
            deleteBuilder.setPositiveButton("Cancel", (dialog, which) -> dialog.dismiss());
            deleteBuilder.show();
        }

        private void setupOpenWeatherUrlPreference() {
            Preference owmToken = findPreference("owm_token");
            if (owmToken != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                String currentToken = prefs.getString("owm_token", "");
                owmToken.setSummary(currentToken.isEmpty() ? "Not set" : "********");

                owmToken.setOnPreferenceClickListener(preference -> {
                    try {
                        Context context = getContext();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.open_weather_map_token);

                        final EditText input = new EditText(context);
                        input.setInputType(InputType.TYPE_CLASS_TEXT);
                        input.setText(currentToken);
                        input.setSelectAllOnFocus(true);

                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(50, 20, 50, 0);
                        layout.addView(input);

                        builder.setView(layout);

                        builder.setPositiveButton("OK", (dialog, which) -> {
                            String url = input.getText().toString().trim();

                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("owm_token", url);
                            editor.apply();
                            owmToken.setSummary("********");
                            Toast.makeText(context, "Token updated", Toast.LENGTH_SHORT).show();
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                        builder.show();
                        return true;
                    } catch (Exception e) {
                        Log.e("Preferences", "Error showing URL dialog", e);
                        return false;
                    }
                });
            }
        }


        private void setupOpenWeatherLatPreference() {
            Preference owmLat = findPreference("owm_lat");
            if (owmLat != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                String currentLat = prefs.getString("owm_lat", "");
                owmLat.setSummary(currentLat);

                owmLat.setOnPreferenceClickListener(preference -> {
                    try {
                        Context context = getContext();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.open_weather_map_lat);

                        final EditText input = new EditText(context);
                        input.setInputType(InputType.TYPE_CLASS_TEXT);
                        input.setText(currentLat);
                        input.setSelectAllOnFocus(true);

                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(50, 20, 50, 0);
                        layout.addView(input);

                        builder.setView(layout);

                        builder.setPositiveButton("OK", (dialog, which) -> {
                            String val = input.getText().toString().trim();

                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("owm_lat", val);
                            editor.apply();
                            owmLat.setSummary(val);
                            Toast.makeText(context, "Lat updated", Toast.LENGTH_SHORT).show();
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                        builder.show();
                        return true;
                    } catch (Exception e) {
                        Log.e("Preferences", "Error showing URL dialog", e);
                        return false;
                    }
                });
            }
        }

        private void setupOpenWeatherLonPreference() {
            Preference owmLong = findPreference("owm_lon");
            if (owmLong != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                String currentLat = prefs.getString("owm_lon", "");
                owmLong.setSummary(currentLat);

                owmLong.setOnPreferenceClickListener(preference -> {
                    try {
                        Context context = getContext();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.open_weather_map_lat);

                        final EditText input = new EditText(context);
                        input.setInputType(InputType.TYPE_CLASS_TEXT);
                        input.setText(currentLat);
                        input.setSelectAllOnFocus(true);

                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(50, 20, 50, 0);
                        layout.addView(input);

                        builder.setView(layout);

                        builder.setPositiveButton("OK", (dialog, which) -> {
                            String val = input.getText().toString().trim();

                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("owm_lon", val);
                            editor.apply();
                            owmLong.setSummary(val);
                            Toast.makeText(context, "Lon updated", Toast.LENGTH_SHORT).show();
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                        builder.show();
                        return true;
                    } catch (Exception e) {
                        Log.e("Preferences", "Error showing URL dialog", e);
                        return false;
                    }
                });
            }
        }



        private void setupOpenWeatherCityPreference() {
            Preference owmCity = findPreference("owm_city");
            if (owmCity != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                String currentCity = prefs.getString("owm_city", "5128581");
                owmCity.setSummary(currentCity);

                owmCity.setOnPreferenceClickListener(preference -> {
                    try {
                        Context context = getContext();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.open_weather_map_token);

                        final EditText input = new EditText(context);
                        input.setInputType(InputType.TYPE_CLASS_TEXT);
                        input.setText(currentCity);
                        input.setSelectAllOnFocus(true);

                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(50, 20, 50, 0);
                        layout.addView(input);

                        builder.setView(layout);

                        builder.setPositiveButton("OK", (dialog, which) -> {
                            String city = input.getText().toString().trim();
                            if (!city.isEmpty()) {
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("owm_city", city);
                                editor.apply();
                                owmCity.setSummary(city);
                                Toast.makeText(context, "City updated", Toast.LENGTH_SHORT).show();
                            }
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                        builder.show();
                        return true;
                    } catch (Exception e) {
                        Log.e("Preferences", "Error showing URL dialog", e);
                        return false;
                    }
                });
            }
        }

        private void setupOpenWeatherTempPreference() {
            Preference owmUnit = findPreference("owm_unit");
            if (owmUnit != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                String currentUnit = prefs.getString("owm_unit", "metric");
                owmUnit.setSummary(currentUnit);

                owmUnit.setOnPreferenceClickListener(preference -> {
                    final String[] options = {"metric", "imperial"};

                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Select a unit system");
                    builder.setItems(options, (dialog, which) -> {
                        String selectedOption = options[which];
                        Log.d("UnitSelection", "Selected option: " + selectedOption);

                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("owm_unit", selectedOption);
                        editor.apply();
                        owmUnit.setSummary(selectedOption);
                    });
                    builder.setCancelable(true);

                    AlertDialog dialog = builder.create();
                    dialog.show();
                    return true;
                });
            }
        }

        private void setupTotpListManagement() {
            Preference totpListPref = findPreference("totp_list_manage");
            if (totpListPref != null) {
                totpListPref.setOnPreferenceClickListener(preference -> {
                    try {
                        Context context = getContext();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.totp_list_add_dialog_title);

                        // Create layout for the dialog
                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(50, 20, 50, 0);

                        // Add name input
                        final EditText nameInput = new EditText(context);
                        nameInput.setHint(R.string.totp_list_name);
                        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
                        layout.addView(nameInput);

                        // Add some spacing
                        View spacing = new View(context);
                        spacing.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 20));
                        layout.addView(spacing);

                        // Add token input
                        final EditText tokenInput = new EditText(context);
                        tokenInput.setHint(R.string.totp_list_token);
                        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT);
                        layout.addView(tokenInput);

                        builder.setView(layout);

                        builder.setPositiveButton("Add", (dialog, which) -> {
                            String name = nameInput.getText().toString().trim();
                            String token = tokenInput.getText().toString().trim();

                            if (!name.isEmpty() && !token.isEmpty()) {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                                String listItemsJson = prefs.getString(TOTP_LIST_ITEMS, "[]");
                                try {
                                    JSONArray listItems = new JSONArray(listItemsJson);
                                    JSONObject newItem = new JSONObject();
                                    newItem.put(TOTP_LIST_ITEM_NAME, name);
                                    newItem.put(TOTP_LIST_ITEM_TOKEN, token);
                                    listItems.put(newItem);

                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.putString(TOTP_LIST_ITEMS, listItems.toString());
                                    editor.apply();

                                    Toast.makeText(context, "TOTP entry added successfully", Toast.LENGTH_SHORT).show();
                                    showTotpListItems(context);
                                } catch (JSONException e) {
                                    Log.e("Preferences", "Error saving TOTP entry", e);
                                    Toast.makeText(context, "Error saving entry", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(context, "Both name and token are required", Toast.LENGTH_SHORT).show();
                            }
                        });

                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                        // Add "View List" button
                        builder.setNeutralButton("View List", (dialog, which) -> {
                            showTotpListItems(context);
                        });

                        builder.show();
                        return true;
                    } catch (Exception e) {
                        Log.e("Preferences", "Error showing TOTP list management dialog", e);
                        return false;
                    }
                });
            }
        }

        private void setupCalendarManagement() {
            Preference calendarPref = findPreference("calendar_list_manage");
            if (calendarPref != null) {
                calendarPref.setOnPreferenceClickListener(preference -> {
                    try {
                        Context context = getContext();
                        if (context == null) return false;

                        // Check for calendar permission
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) 
                            != PackageManager.PERMISSION_GRANTED) {
                            
                            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, 1);
                            Toast.makeText(context, R.string.calendar_permission_required, Toast.LENGTH_SHORT).show();
                            return true;
                        }

                        // Get list of calendars
                        ContentResolver contentResolver = context.getContentResolver();
                        Uri uri = CalendarContract.Calendars.CONTENT_URI;
                        String[] projection = new String[]{
                            CalendarContract.Calendars._ID,
                            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                            CalendarContract.Calendars.ACCOUNT_NAME
                        };

                        Cursor cursor = contentResolver.query(uri, projection, null, null, null);
                        if (cursor == null) {
                            Toast.makeText(context, R.string.calendar_list_empty, Toast.LENGTH_SHORT).show();
                            return true;
                        }

                        ArrayList<CalendarInfo> calendars = new ArrayList<>();
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        Set<String> selectedCalendars = prefs.getStringSet(CALENDAR_SYNC_ITEMS, new HashSet<>());

                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(0);
                            String displayName = cursor.getString(1);
                            String accountName = cursor.getString(2);
                            calendars.add(new CalendarInfo(id, displayName, accountName));
                        }
                        cursor.close();

                        if (calendars.isEmpty()) {
                            Toast.makeText(context, R.string.calendar_list_empty, Toast.LENGTH_SHORT).show();
                            return true;
                        }

                        // Create dialog with checkboxes
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.calendar_list_title);

                        String[] calendarNames = new String[calendars.size()];
                        boolean[] checkedItems = new boolean[calendars.size()];
                        
                        for (int i = 0; i < calendars.size(); i++) {
                            CalendarInfo calendar = calendars.get(i);
                            calendarNames[i] = calendar.displayName + " (" + calendar.accountName + ")";
                            checkedItems[i] = selectedCalendars.contains(String.valueOf(calendar.id));
                        }

                        builder.setMultiChoiceItems(calendarNames, checkedItems, (dialog, which, isChecked) -> {
                            // Update checked state
                            checkedItems[which] = isChecked;
                        });

                        builder.setPositiveButton("Save", (dialog, which) -> {
                            // Save selected calendars
                            Set<String> newSelection = new HashSet<>();
                            for (int i = 0; i < calendars.size(); i++) {
                                if (checkedItems[i]) {
                                    newSelection.add(String.valueOf(calendars.get(i).id));
                                }
                            }
                            
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putStringSet(CALENDAR_SYNC_ITEMS, newSelection);
                            editor.apply();
                            
                            Toast.makeText(context, "Calendar selection saved", Toast.LENGTH_SHORT).show();
                        });

                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                        builder.show();
                        return true;
                    } catch (Exception e) {
                        Log.e("Preferences", "Error showing calendar management dialog", e);
                        return false;
                    }
                });
            }
        }

        private static class CalendarInfo {
            long id;
            String displayName;
            String accountName;

            CalendarInfo(long id, String displayName, String accountName) {
                this.id = id;
                this.displayName = displayName;
                this.accountName = accountName;
            }
        }

        private void showTotpListItems(Context context) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String listItemsJson = prefs.getString(TOTP_LIST_ITEMS, "[]");
            try {
                JSONArray listItems = new JSONArray(listItemsJson);
                if (listItems.length() == 0) {
                    Toast.makeText(context, R.string.totp_list_empty, Toast.LENGTH_SHORT).show();
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("TOTP Entries");

                String[] items = new String[listItems.length()];
                for (int i = 0; i < listItems.length(); i++) {
                    JSONObject item = listItems.getJSONObject(i);
                    items[i] = item.getString(TOTP_LIST_ITEM_NAME);
                }

                builder.setItems(items, (dialog, which) -> {
                    // Show edit dialog for selected item
                    JSONObject selectedItem;
                    try {
                        selectedItem = listItems.getJSONObject(which);
                        showEditTotpDialog(context, selectedItem, which);
                    } catch (JSONException e) {
                        Log.e("Preferences", "Error getting selected item", e);
                    }
                });

                builder.setPositiveButton("Close", null);

                builder.show();
            } catch (JSONException e) {
                Log.e("Preferences", "Error showing TOTP list", e);
                Toast.makeText(context, "Error showing list", Toast.LENGTH_SHORT).show();
            }
        }

        private void showEditTotpDialog(Context context, JSONObject item, int position) {
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.totp_list_edit);

                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(50, 20, 50, 0);

                final EditText nameInput = new EditText(context);
                nameInput.setHint(R.string.totp_list_name);
                nameInput.setText(item.getString(TOTP_LIST_ITEM_NAME));
                layout.addView(nameInput);

                View spacing = new View(context);
                spacing.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 20));
                layout.addView(spacing);

                final EditText tokenInput = new EditText(context);
                tokenInput.setHint(R.string.totp_list_token);
                tokenInput.setText(item.getString(TOTP_LIST_ITEM_TOKEN));
                layout.addView(tokenInput);

                builder.setView(layout);

                builder.setPositiveButton("Save", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String token = tokenInput.getText().toString().trim();

                    if (!name.isEmpty() && !token.isEmpty()) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        String listItemsJson = prefs.getString(TOTP_LIST_ITEMS, "[]");
                        try {
                            JSONArray listItems = new JSONArray(listItemsJson);
                            JSONObject updatedItem = new JSONObject();
                            updatedItem.put(TOTP_LIST_ITEM_NAME, name);
                            updatedItem.put(TOTP_LIST_ITEM_TOKEN, token);
                            listItems.put(position, updatedItem);

                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString(TOTP_LIST_ITEMS, listItems.toString());
                            editor.apply();

                            Toast.makeText(context, "TOTP entry updated", Toast.LENGTH_SHORT).show();
                            showTotpListItems(context);
                        } catch (JSONException e) {
                            Log.e("Preferences", "Error updating TOTP entry", e);
                            Toast.makeText(context, "Error updating entry", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(context, "Both name and token are required", Toast.LENGTH_SHORT).show();
                    }
                });

                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder.setNeutralButton("Delete", (dialog, which) -> {
                    showDeleteTotpConfirmation(context, position);
                });

                builder.show();
            } catch (JSONException e) {
                Log.e("Preferences", "Error showing edit dialog", e);
                Toast.makeText(context, "Error showing edit dialog", Toast.LENGTH_SHORT).show();
            }
        }

        private void showDeleteTotpConfirmation(Context context, int position) {
            AlertDialog.Builder deleteBuilder = new AlertDialog.Builder(context);
            deleteBuilder.setTitle("Delete TOTP Entry");
            deleteBuilder.setMessage("Are you sure you want to delete this TOTP entry?");

            deleteBuilder.setPositiveButton("Delete", (dialog, which) -> {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                String listItemsJson = prefs.getString(TOTP_LIST_ITEMS, "[]");
                try {
                    JSONArray listItems = new JSONArray(listItemsJson);
                    listItems.remove(position);

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(TOTP_LIST_ITEMS, listItems.toString());
                    editor.apply();

                    Toast.makeText(context, "TOTP entry deleted", Toast.LENGTH_SHORT).show();
                    showTotpListItems(context);
                } catch (JSONException e) {
                    Log.e("Preferences", "Error deleting TOTP entry", e);
                    Toast.makeText(context, "Error deleting entry", Toast.LENGTH_SHORT).show();
                }
            });

            deleteBuilder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            deleteBuilder.show();
        }
    }
}