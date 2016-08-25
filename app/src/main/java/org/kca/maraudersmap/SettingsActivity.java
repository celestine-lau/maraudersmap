/*
    Marauder's Map - Pokemon Go pokescanner
    Copyright (C) 2016  Celestine Lau

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package org.kca.maraudersmap;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;

public class SettingsActivity extends Activity implements
        SharedPreferences.OnSharedPreferenceChangeListener
{
    private SettingsFragment settingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        settingsFragment = new SettingsFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, settingsFragment)
                .commit();


    }

    @Override
    public void onResume()
    {
        super.onResume();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        int index = Integer.parseInt(sharedPref.getString(getString(R.string.pref_scan_radius), "1"));
        updateScanRadiusSummary(index);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        Preference pref = settingsFragment.findPreference(key);
        if (key.equals(getString(R.string.pref_scan_radius)))
        {
            int index = Integer.parseInt(sharedPreferences.getString(getString(R.string.pref_scan_radius), "1"));
            updateScanRadiusSummary(index);
        }
    }

    private void updateScanRadiusSummary(int scanRadius)
    {
        Preference pref = settingsFragment.findPreference(getString(R.string.pref_scan_radius));
        String[] values = getResources().getStringArray(R.array.pref_scan_radius_entries);
        pref.setSummary(values[scanRadius]);
    }
}
