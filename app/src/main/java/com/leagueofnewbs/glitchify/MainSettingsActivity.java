package com.leagueofnewbs.glitchify;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

import java.io.File;

import de.robv.android.xposed.XposedBridge;

public class MainSettingsActivity extends AppCompatActivity {
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MainSettingsFragment()).commit();
    }

    public static class MainSettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            PreferenceManager pref = getPreferenceManager();
            pref.setSharedPreferencesName("preferences");
            addPreferencesFromResource(R.xml.preferences);
        }

        @Override
        public void onPause() {
            super.onPause();

            File dataDir = new File(getActivity().getApplicationInfo().dataDir);
            dataDir.setReadable(true, false);
            dataDir.setExecutable(true, false);
            File prefsDir = new File(getActivity().getApplicationInfo().dataDir, "shared_prefs");
            prefsDir.setReadable(true, false);
            prefsDir.setExecutable(true, false);
            File prefsFile = new File(prefsDir, getPreferenceManager().getSharedPreferencesName() + ".xml");
            if (prefsFile.exists()) {
                boolean result = prefsFile.setReadable(true, false);

                if (!result) {
                    XposedBridge.log("LoN: Could not set preferences as readable, settings will not be loaded!");
                }
            }
        }
    }
}
