package com.miteos.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

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

            EditTextPreference hassUrlPref = findPreference("hassUrl");

            if (hassUrlPref != null) {
                hassUrlPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    Log.d("Preferences", String.format("Notifications enabled: %s", newValue));
                    return true; // Return true if the event is handled.
                });
            }
        }
    }
}