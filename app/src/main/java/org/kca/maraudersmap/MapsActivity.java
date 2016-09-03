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

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.pokegoapi.api.PokemonGo;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks,
        LocationListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        ScanListener,
        ServiceConnection
{
    private static final String TAG = "MapsActivity";
    private static final Pattern GITHUB_RELEASE_REGEX = Pattern.compile("/tree/(.+?)\"");
    /** Arbitrary request ID for location permission request */
    private static final int LOCATION_PERMISSION_REQUEST_ID = 7;
    /** The fastest interval in ms between location updates */
    private static final long DEFAULT_FASTEST_LOCATION_REQUEST_INTERVAL = 8000;
    /** The standard interval in ms between location updates */
    private static final long DEFAULT_LOCATION_REQUEST_INTERVAL = 12000;
    private static final float DEFAULT_LAT = 1.255651f;
    private static final float DEFAULT_LNG = 103.822159f;
    /** Radius of a single scan, in km */
    private static final double SCAN_RADIUS = 0.07;
    private static final double KM_PER_DEGREE = 111;
    /**
     * The parameters for the scan. SCAN_PARAMETERS[i][0] represents
     * the distance from the middle (expressed in multiples of SCAN_RADIUS) the scan is
     * to be performed in the ith level of scanning, and SCAN_PARAMETERS[i][1] represents the
     * number of scans at that distance that will be performed. Each scan is performed at equal
     * angle apart. For example, {1.7,6} represents 6 scans will be performed at 1.7*SCAN_RADIUS
     * away from the middle, at 360/6=60 degree intervals.
     */
    private static final double[][] SCAN_PARAMETERS = {
            {0, 1},
            {1.7, 6},
            {3.4, 12}
    };
    private static final int LOCATION_REQUEST_ID = 20056;
    public static final String EXTRA_SCAN_RESULT = "org.kca.maraudersmap.extra.SCAN_RESULT";

    private SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private List<Marker> markersOnMap;
    private LatLng myLocation = new LatLng(DEFAULT_LAT, DEFAULT_LNG);

    /** The large circle indicating the range of the scan */
    private Circle locationCircle;
    private GoogleMap mMap;
    private GoogleApiClient googleApiClient;
    private SharedPreferences sharedPref;
    private int scanSize;
    private boolean scanRunning;
    private ScanResult backgroundScanResult;
    private PendingIntent locationRequestPendingIntent;
    private BackgroundService backgroundService;
    private CheckForUpdatesTask checkForUpdatesTask;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        markersOnMap = new ArrayList<Marker>();
        if (googleApiClient == null)
        {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        googleApiClient.connect();
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        init();
        loadFromPreferences(sharedPref);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        Intent startIntent = getIntent();
        if (startIntent.hasExtra(EXTRA_SCAN_RESULT))
        {
            backgroundScanResult = (ScanResult)startIntent.getParcelableExtra(EXTRA_SCAN_RESULT);
        }
        else
        {
            /* Don't check for updates */
            checkForUpdatesTask = new CheckForUpdatesTask();
            checkForUpdatesTask.execute();
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        // Bind to background service
        Intent serviceIntent = new Intent(this, BackgroundService.class);
        bindService(serviceIntent, this, BIND_AUTO_CREATE);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        registerForegroundLocationUpdates();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (googleApiClient.isConnected())
        {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (checkForUpdatesTask != null)
        {
            checkForUpdatesTask.cancel(true);
        }
        unbindService(this);
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Initializes basic parameters for the activity.
     */
    private void init()
    {
        scanRunning = false;
    }

    /**
     * Loads credentials from the shared preferences.
     * @param pref
     */
    private void loadFromPreferences(SharedPreferences pref)
    {
        scanSize = Integer.parseInt(pref.getString(getString(R.string.pref_scan_radius), "1"));
        if (locationCircle != null)
        {
            locationCircle.setRadius(getScanRadius(scanSize));
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap)
    {
        mMap = googleMap;
        float lastLat = sharedPref.getFloat(getString(R.string.pref_last_lat), DEFAULT_LAT);
        float lastLng = sharedPref.getFloat(getString(R.string.pref_last_lng), DEFAULT_LNG);
        myLocation = new LatLng(lastLat, lastLng);
        CameraPosition cameraPos = CameraPosition.builder()
                .target(myLocation)
                .zoom(18)
                .build();
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos));
        CircleOptions copt = new CircleOptions()
                .center(myLocation)
                .radius(getScanRadius(scanSize))
                .fillColor(ContextCompat.getColor(this, R.color.colorLocationCircle))
                .strokeWidth(3)
                .strokeColor(Color.RED)
                .visible(true);
        locationCircle = mMap.addCircle(copt);
        if (backgroundScanResult != null)
        {
            onScanResult(backgroundScanResult);
            backgroundScanResult = null;
        }
    }

    /**
     * Called when the scan button is pressed
     * @param v the view that triggered this function
     */
    public void scanPressed(View v)
    {
        for (Marker marker : markersOnMap)
        {
            marker.remove();
        }
        try
        {
            Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            myLocation = new LatLng(location.getLatitude(), location.getLongitude());
            updateLocationCircles();
            if (backgroundService != null)
            {
                toggleScanButton(false);
                Toast.makeText(this, "Scan started", Toast.LENGTH_SHORT).show();
                backgroundService.foregroundScan(location.getLatitude(), location.getLongitude(), this);
            }
            else
            {
                Toast.makeText(this, "Background service not running", Toast.LENGTH_SHORT).show();
            }
        }
        catch (SecurityException e)
        {

        }
    }

    /**
     * Toggles whether the scan button is enabled
     * @param enabled set to true to enable the scan button, false to disable
     */
    public void toggleScanButton(boolean enabled)
    {
        ImageButton scanButton = (ImageButton)findViewById(R.id.scanButton);
        scanRunning = !enabled;
        if (enabled)
        {
            scanButton.setEnabled(true);
            scanButton.setImageResource(R.drawable.scan_button);
        }
        else
        {
            scanButton.setEnabled(false);
            scanButton.setImageResource(R.drawable.scan_button_disabled);
        }
    }

    /**
     * Called when settings button is pressed
     * @param v the view that triggered this function
     */
    public void settingsPressed(View v)
    {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        if ( ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION ) !=
                PackageManager.PERMISSION_GRANTED ) {

            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_ID);
        }
        else
        {
            mMap.setMyLocationEnabled(true);
            registerForegroundLocationUpdates();
            processBackgroundLocationScan();
        }
    }

    /**
     * Registers foreground location updates
     */
    private void registerForegroundLocationUpdates()
    {
        if (googleApiClient.isConnected())
        {
            LocationRequest locationRequest = createLocationRequest(DEFAULT_LOCATION_REQUEST_INTERVAL);
            try
            {
                LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest,
                        this);
            } catch (SecurityException e)
            {
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        Log.w(TAG, "GoogleApis connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
    {
        Log.w(TAG, connectionResult.getErrorMessage());
    }

    @Override
    public void onLocationChanged(Location location)
    {
        myLocation = new LatLng(location.getLatitude(), location.getLongitude());
        SharedPreferences.Editor ed = sharedPref.edit();
        ed.putFloat(getString(R.string.pref_last_lat), (float)myLocation.latitude);
        ed.putFloat(getString(R.string.pref_last_lng), (float)myLocation.longitude);
        ed.apply();
        if (!scanRunning)
        {
            updateLocationCircles();
        }
    }

    /**
     * Updates the circle indicating the scan radius.
     */
    private void updateLocationCircles()
    {
        locationCircle.setCenter(myLocation);
    }

    /**
     * Sets up the background location scan request, if background scan is enabled
     */
    private void processBackgroundLocationScan()
    {
        if (sharedPref.getBoolean(getString(R.string.pref_background_scan), false))
        {
            int scanFrequency = Integer.parseInt(sharedPref.getString(getString(R.string.pref_scan_frequency),
                    getResources().getString(R.string.pref_scan_frequency_default)));
            LocationRequest backgroundLocationRequest = createLocationRequest(scanFrequency * 60000);
            Intent intent = new Intent(this, LocationReceiver.class);
            intent.setAction(getString(R.string.action_location_update));
            if (locationRequestPendingIntent != null)
            {
                Log.d("LocationReceiver", "Cancelling existing location updates");
                LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, locationRequestPendingIntent);
            }
            locationRequestPendingIntent = PendingIntent.getBroadcast(this, LOCATION_REQUEST_ID,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);
            try
            {
                LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, backgroundLocationRequest,
                        locationRequestPendingIntent);
                Log.d("LocationReceiver", "requested location updates at " + scanFrequency + " min intervals");
            }
            catch (SecurityException e)
            {

            }
        }
        else
        {
            if (locationRequestPendingIntent != null)
            {
                Log.d("LocationReceiver", "Cancelled location updates");
                LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, locationRequestPendingIntent);
                locationRequestPendingIntent = null;
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        if (requestCode == LOCATION_PERMISSION_REQUEST_ID)
        {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION ) ==
                    PackageManager.PERMISSION_GRANTED)
            {
                LocationRequest activeLocationRequest = createLocationRequest(DEFAULT_LOCATION_REQUEST_INTERVAL);
                mMap.setMyLocationEnabled(true);
                LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, activeLocationRequest,
                        this);
                processBackgroundLocationScan();
            }
            else
            {
                Toast.makeText(this, "Marauders map requires location permissions to run.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        loadFromPreferences(sharedPreferences);
        if (key.equals(getString(R.string.pref_scan_frequency))
                || key.equals(getString(R.string.pref_background_scan)))
        {
            processBackgroundLocationScan();
        }
    }

    @Override
    public void onScanResult(ScanResult result)
    {
        for (ScanResult.MarkerParams mParams : result)
        {
            BitmapDescriptor bitmapDescriptor = null;
            switch (mParams.type)
            {
                case 0: // scan point
                    bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
                    break;
                default: // pokemon

                    int resId = getResources().obtainTypedArray(R.array.pokemon_resource_ids)
                            .getResourceId(mParams.type-1, 0);
                    Bitmap bm = BitmapFactory.decodeResource(getResources(), resId);
                    bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bm);

                    break;
            }
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(mParams.latitude, mParams.longitude))
                    .icon(bitmapDescriptor)
                    .title(mParams.text));
            markersOnMap.add(marker);
        }
    }

    @Override
    public void onScanError(String errorMessage)
    {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
        toggleScanButton(true);
    }

    @Override
    public void onScanComplete()
    {
        toggleScanButton(true);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service)
    {
        BackgroundService.BackgroundServiceBinder binder = (BackgroundService.BackgroundServiceBinder)service;
        backgroundService = binder.getService();
        Log.d(TAG, "background service bound!");
    }

    @Override
    public void onServiceDisconnected(ComponentName name)
    {
        backgroundService = null;
    }

    /**
     * Checks for updates from Github.
     * Thanks to javiersantos who provided a handy reference for the code at:
     * https://github.com/javiersantos/AppUpdater.
     */
    private class CheckForUpdatesTask extends AsyncTask<Void, Void, Boolean>
    {


        @Override
        protected Boolean doInBackground(Void... voids)
        {
            OkHttpClient httpClient = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(getString(R.string.github_releases_url))
                    .build();
            ResponseBody body = null;
            try
            {
                Response response = httpClient.newCall(request).execute();
                body = response.body();
                Scanner sc = new Scanner(body.byteStream(), "UTF-8");
                String version = null;
                while (sc.hasNextLine())
                {
                    String line = sc.nextLine();
                    Matcher matcher = GITHUB_RELEASE_REGEX.matcher(line);
                    if (matcher.find())
                    {
                        version = matcher.group(1);
                        break;
                    }
                }
                if (version != null)
                {
                    Context context = MapsActivity.this;
                    try
                    {
                        String currentVersion = context.getPackageManager()
                                .getPackageInfo(context.getPackageName(), 0).versionName;
                        Log.d(TAG, "current version is " + currentVersion + " and online version is " + version);
                        return !currentVersion.equals(version);
                    }
                    catch (PackageManager.NameNotFoundException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
            catch (IOException e)
            {

            }
            finally
            {
                body.close();
            }

            return true;
        }

        protected void onPostExecute(Boolean result)
        {
            if (result)
            {
                new AlertDialog.Builder(MapsActivity.this)
                        .setTitle("Update available")
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setMessage("There is an update available for this app. Visit the download page now?")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i)
                            {
                                String url = MapsActivity.this.getString(R.string.github_releases_url);
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse(url));
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();

            }
        }
    }

    /**
     * Creates the location request for the fused location API to request location updates
     * @param interval the normal interval between location updates in ms
     * @return the location request object
     */
    private static LocationRequest createLocationRequest(long interval)
    {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(interval);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setFastestInterval((interval * 2) / 3);
        return locationRequest;
    }

    /**
     * Gets the radius of the scan, based on the scanSize preference
     * @param scanSize the scanSize preference
     * @return the radius of the scan, in meters
     */
    private static double getScanRadius(int scanSize)
    {
        return SCAN_RADIUS * 1000 * (SCAN_PARAMETERS[scanSize][0] + 1);
    }
}
