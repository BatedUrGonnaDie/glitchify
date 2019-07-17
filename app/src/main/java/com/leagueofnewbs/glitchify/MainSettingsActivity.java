package com.leagueofnewbs.glitchify;

import android.annotation.SuppressLint;
import android.os.Build;
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                pref.setStorageDeviceProtected();
            }
            pref.setSharedPreferencesName("preferences");
            addPreferencesFromResource(R.xml.preferences);
        }

        @SuppressLint("SetWorldReadable")
        @Override
        public void onPause() {
            super.onPause();
            android.content.pm.ApplicationInfo info = getActivity().getApplicationInfo();
            String dataPath;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dataPath = info.deviceProtectedDataDir;
            } else {
                dataPath = info.dataDir;
            }
            File dataDir = new File(dataPath);
            boolean setReadResult = dataDir.setReadable(true, false);
            boolean setExecResult = dataDir.setExecutable(true, false);
            if (!setReadResult  && !setExecResult)
                XposedBridge.log("LoN: Cannot set permissions for preferences!");

            File prefsDir = new File(dataPath, "shared_prefs");
            setReadResult = prefsDir.setReadable(true, false);
            setExecResult = prefsDir.setExecutable(true, false);
            if (!setReadResult && !setExecResult)
                XposedBridge.log("LoN: Cannot set permissions for preferences!");

            PreferenceManager pref = getPreferenceManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                pref.setStorageDeviceProtected();
            }
            File prefsFile = new File(prefsDir, pref.getSharedPreferencesName() + ".xml");
            if (prefsFile.exists()) {
                boolean result = prefsFile.setReadable(true, false);

                if (!result) {
                    XposedBridge.log("LoN: Could not set preferences as readable, settings will not be loaded!");
                }
            }
        }
    }
}
