package com.leagueofnewbs.glitchify;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

import java.io.File;

import de.robv.android.xposed.XposedBridge;

public class MainSettingsActivity extends AppCompatActivity {
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


        @SuppressLint("SetWorldReadable")
        @Override
        public void onPause() {
            super.onPause();

            File dataDir = new File(getActivity().getApplicationInfo().dataDir);
            boolean temp = dataDir.setReadable(true, false);
            boolean temp2 = dataDir.setExecutable(true, false);
            if (!temp  && !temp2)
                XposedBridge.log("LoN: Cannot set permissions for preferences!");

            File prefsDir = new File(getActivity().getApplicationInfo().dataDir, "shared_prefs");
            temp = prefsDir.setReadable(true, false);
            temp2 = prefsDir.setExecutable(true, false);
            if (!temp && !temp2)
                XposedBridge.log("LoN: Cannot set permissions for preferences!");

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
