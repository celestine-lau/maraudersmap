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

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
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
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.map.pokemon.CatchablePokemon;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.OkHttpClient;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks,
        LocationListener
{
    //LatLng myLocation = new LatLng(1.291215, 103.788256);
    private static final String TAG = "MapsActivity";
    private static final int LOCATION_PERMISSION_REQUEST_ID = 7;
    private static final long DEFAULT_FASTEST_LOCATION_REQUEST_INTERVAL = 20000;
    private static final long DEFAULT_LOCATION_REQUEST_INTERVAL = 30000;
    private SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("kk:mm:ss");
    private List<Marker> markersOnMap;
    private LatLng myLocation = new LatLng(1.255651,103.822159);
    /** The large circle indicating the range of the scan */
    private Circle locationCircle;
    /** The small circle indicating the center of the range of the scan */
    private Circle locationCircleCenter;
    private GoogleMap mMap;
    private boolean firstScan;
    private OkHttpClient httpClient;
    private PtcCredentialProvider ptcCredentialProvider;
    private PokemonGo go;
    private GoogleApiClient googleApiClient;


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
        firstScan = true;
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
        CameraPosition cameraPos = CameraPosition.builder()
                .target(myLocation)
                .zoom(18)
                .build();
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos));
        CircleOptions copt = new CircleOptions()
                .center(myLocation)
                .radius(70)
                .fillColor(ContextCompat.getColor(this, R.color.colorLocationCircle))
                .strokeWidth(3)
                .strokeColor(Color.RED)
                .visible(true);
        locationCircle = mMap.addCircle(copt);

    }

    /**
     * Called when the scan button is pressed
     * @param v the view that triggered this function
     */
    public void scanPressed(View v)
    {
        Toast.makeText(this, "Scan started", Toast.LENGTH_SHORT).show();
        new ScanPokemonTask().execute();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        LocationRequest locationRequest = createLocationRequest();
        if ( ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION ) !=
                PackageManager.PERMISSION_GRANTED ) {

            ActivityCompat.requestPermissions( this, new String[] {  android.Manifest.permission.ACCESS_COARSE_LOCATION  },
                    LOCATION_PERMISSION_REQUEST_ID);
        }
        else
        {
            mMap.setMyLocationEnabled(true);
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest,
                    this);
        }
    }

    /**
     * Creates the location request for the fused location API to request location updates
     * @return the location request object
     */
    private LocationRequest createLocationRequest()
    {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(DEFAULT_LOCATION_REQUEST_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setFastestInterval(DEFAULT_FASTEST_LOCATION_REQUEST_INTERVAL);
        return locationRequest;
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
        updateLocationCircles();
    }

    private void updateLocationCircles()
    {
        locationCircle.setCenter(myLocation);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        if (requestCode == LOCATION_PERMISSION_REQUEST_ID)
        {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION ) ==
                    PackageManager.PERMISSION_GRANTED)
            {
                mMap.setMyLocationEnabled(true);
                LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, createLocationRequest(),
                        this);
            }
        }
    }

    /**
     * This task scans for pokemon in the vicinity and updates the map with the markers
     * corresponding to each pokemon.
     */
    private class ScanPokemonTask extends AsyncTask<Void, Void, Boolean>
    {
        private List<CatchablePokemon> pokeList;

        public ScanPokemonTask()
        {
            pokeList = new ArrayList<CatchablePokemon>();
        }

        @Override
        protected void onPreExecute()
        {
            for (Marker marker : markersOnMap)
            {
                marker.remove();
            }
        }

        @Override
        protected Boolean doInBackground(Void... voids)
        {
            try
            {
                if (firstScan)
                {
                    httpClient = new OkHttpClient();
                    ptcCredentialProvider = new PtcCredentialProvider(httpClient, "mewtree1551", "defghijk");
                    go = new PokemonGo(ptcCredentialProvider, httpClient);
                    firstScan = false;
                }
                go.setLocation(myLocation.latitude, myLocation.longitude, 20);
                List<CatchablePokemon> list = go.getMap().getCatchablePokemon();
                pokeList.addAll(list);
            }
            catch (RemoteServerException e)
            {
                e.printStackTrace();
                return false;
            }
            catch (LoginFailedException e)
            {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result)
        {
            if (result)
            {
                if (pokeList.isEmpty())
                {
                    Toast.makeText(MapsActivity.this, "Sorry no pokemons", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    StringBuffer sb = new StringBuffer();

                    for (CatchablePokemon pokemon : pokeList)
                    {
                        String markerText = pokemon.getPokemonId().toString() + " " +
                                TIME_FORMAT.format(new Date(pokemon.getExpirationTimestampMs()));
                        Marker marker = mMap.addMarker(new MarkerOptions().position(
                                new LatLng(pokemon.getLatitude(), pokemon.getLongitude())).title(markerText));
                        markersOnMap.add(marker);
                    }
                }
            }
            else
            {
                Toast.makeText(MapsActivity.this, "Sorry there was an error", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
