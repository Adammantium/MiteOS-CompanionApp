package com.miteos.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

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
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference button = findPreference("pairWatch");
            if(button != null) {
                button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent myIntent = new Intent(OverviewActivity.current, MainActivity.class);
                        startActivity(myIntent);
                        return true;
                    }
                });
            }

            // Handle Home Assistant URL preference
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

            // Handle Home Assistant API Token preference
            Preference hassTokenPref = findPreference("hass_token");
            if (hassTokenPref != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                String currentToken = prefs.getString("hass_token", "");
                // Show masked token in summary
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
    }
}