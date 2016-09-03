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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.CheckBoxPreference;
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
        updateListPreferenceSummary(sharedPref,
                R.string.pref_scan_radius, R.array.pref_scan_radius_entries, R.array.pref_scan_radius_values);
        updateListPreferenceSummary(sharedPref,
                R.string.pref_scan_frequency, R.array.pref_scan_frequency_entries, R.array.pref_scan_frequency_values);
        updateListPreferenceSummary(sharedPref,
                R.string.pref_notify_pokemon_rarity, R.array.pref_pokemon_rarity_entries, R.array.pref_pokemon_rarity_values);
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
        final Preference pref = settingsFragment.findPreference(key);
        if (key.equals(getString(R.string.pref_scan_radius)))
        {
            updateListPreferenceSummary(sharedPreferences,
                    R.string.pref_scan_radius, R.array.pref_scan_radius_entries,
                    R.array.pref_scan_radius_values);
        }
        else if (key.equals(getString(R.string.pref_scan_frequency)))
        {
            updateListPreferenceSummary(sharedPreferences,
                    R.string.pref_scan_frequency, R.array.pref_scan_frequency_entries,
                    R.array.pref_scan_frequency_values);
        }
        else if (key.equals(getString(R.string.pref_notify_pokemon_rarity)))
        {
            updateListPreferenceSummary(sharedPreferences,
                    R.string.pref_notify_pokemon_rarity, R.array.pref_pokemon_rarity_entries,
                    R.array.pref_pokemon_rarity_values);
        }
    }

    /**
     * Updates the summary text of a list preference
     * @param sharedPref the shared preferences object
     * @param prefStrId the string resource id of the preference
     * @param listEntryArrayId the array resource id of the list entries
     * @param listValuesArrayId the array resource id of the list values
     */
    private void updateListPreferenceSummary(SharedPreferences sharedPref, int prefStrId,
                                             int listEntryArrayId, int listValuesArrayId)
    {
        String storedVal = sharedPref.getString(getString(prefStrId), "0");
        Preference pref = settingsFragment.findPreference(getString(prefStrId));
        String[] entries = getResources().getStringArray(listEntryArrayId);
        String[] values = getResources().getStringArray(listValuesArrayId);
        for (int i = 0; i < values.length; i++)
        {
            if (storedVal.equals(values[i]))
            {
                pref.setSummary(entries[i]);
            }
        }
    }
}
