package app.beaglecopter.controller;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by jungtaek.kim on 2015-06-24.
 */

public class SettingsActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);
    }
}
