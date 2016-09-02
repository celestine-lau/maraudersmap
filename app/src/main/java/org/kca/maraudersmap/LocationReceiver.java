package org.kca.maraudersmap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.LocationResult;

public class LocationReceiver extends BroadcastReceiver
{
    private static final String TAG = "LocationReceiver";
    public LocationReceiver()
    {
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (context.getString(R.string.action_location_update).equals(intent.getAction()))
        {
            Log.d(TAG, "Location update received");
            if (LocationResult.hasResult(intent))
            {
                LocationResult locationResult = LocationResult.extractResult(intent);
                Location location = locationResult.getLastLocation();
                BackgroundService.startActionLocationUpdated(context, location.getLatitude(), location.getLongitude());
            }
        }
    }
}
