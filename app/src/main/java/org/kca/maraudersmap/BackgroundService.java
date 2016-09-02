package org.kca.maraudersmap;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.maps.model.LatLng;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.map.pokemon.CatchablePokemon;
import com.pokegoapi.api.map.pokemon.encounter.EncounterResult;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.AsyncPokemonGoException;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import POGOProtos.Data.PokemonDataOuterClass;
import okhttp3.OkHttpClient;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class BackgroundService extends IntentService
{
    /* Informs the service that the location has been updated */
    private static final String ACTION_LOCATION_UPDATED = "org.kca.maraudersmap.action.LOCATION_UPDATED";
    private static final String EXTRA_LATITUDE = "org.kca.maraudersmap.extra.LATITUDE";
    private static final String EXTRA_LONGITUDE = "org.kca.maraudersmap.extra.LONGITUDE";
    /** Radius of a single scan, in km */
    private static final double SCAN_RADIUS = 0.07;
    private static final double KM_PER_DEGREE = 111;
    private static final String TAG = "BackgroundService";
    private SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

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
    private OkHttpClient httpClient;
    private List<Pair<String, String>> credentials;
    private PokemonGo[] go;
    private int[] pokemonRarities;
    private Executor exeggutor;
    private boolean loggedIn;

    public BackgroundService()
    {
        super("BackgroundService");
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        exeggutor = new SerialExecutor(new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(5)));
        credentials = new ArrayList<Pair<String, String>>();
        loggedIn = false;
        pokemonRarities = getApplicationContext().getResources().getIntArray(R.array.pokemon_rarities);
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return new BackgroundServiceBinder();
    }

    /**
     * Starts this service to perform action LocationUpdated with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionLocationUpdated(Context context, double latitude, double longitude)
    {
        Intent intent = new Intent(context, BackgroundService.class);
        intent.setAction(ACTION_LOCATION_UPDATED);
        intent.putExtra(EXTRA_LATITUDE, latitude);
        intent.putExtra(EXTRA_LONGITUDE, longitude);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent)
    {
        if (intent != null)
        {
            final String action = intent.getAction();
            if (ACTION_LOCATION_UPDATED.equals(action))
            {
                final double latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0);
                final double longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0);
                backgroundScan(latitude, longitude);
            }
        }
    }

    /**
     * Handle the location updated action
     * @param latitude the updated latitude
     * @param longitude the updated longitude
     */
    private void backgroundScan(final double latitude, final double longitude)
    {
        exeggutor.execute(
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        ScanPokemonTask task = new ScanPokemonTask(getApplicationContext(), null);
                        task.performScan(latitude, longitude);
                    }
                }
        );
    }

    /**
     * Performs a foreground scan at a specified location. Results are returned to the ScanListener
     * via the callbacks onScanError, onScanResult, and onScanComplete
     * @param latitude the latitude of the position to scan at
     * @param longitude the longitude of the position to scan at
     * @param scanListener the scan listener to report to
     */
    public void foregroundScan(double latitude, double longitude, ScanListener scanListener)
    {
        ScanPokemonTask task = new ScanPokemonTask(getApplicationContext(), scanListener);
        task.executeOnExecutor(exeggutor, latitude, longitude);
    }

    public class BackgroundServiceBinder extends Binder
    {
        /**
         * Return the current instance of BackgroundService
         * @return the current instance of BackgroundService
         */
        public BackgroundService getService()
        {
            return BackgroundService.this;
        }
    }

    /**
     * This task scans for pokemon in the vicinity and updates the map with the markers
     * corresponding to each pokemon.
     */
    private class ScanPokemonTask extends AsyncTask<Double, ScanResult, Boolean>
    {
        private Set<CatchablePokemon> pokeSet;
        private List<LatLng> scanPoints;
        private boolean showIvs;
        private int scanSize;
        private ScanListener listener;
        private ScanResult scanResult;
        private String error;

        /**
         * Creates a ScanPokemonTask for background scanning
         * @param context the application context
         */
        public ScanPokemonTask(Context context)
        {
            error = "";
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            String usernamesList = sharedPreferences.getString(context.getString(R.string.pref_usernames), "");
            String passwordsList = sharedPreferences.getString(context.getString(R.string.pref_passwords), "");
            String[] usernames = usernamesList.split(" ");
            String[] passwords = passwordsList.split(" ");
            for (int i = 0; i < Math.min(usernames.length, passwords.length); i++)
            {
                Pair<String, String> credential = new Pair<String, String>(usernames[i], passwords[i]);
                if (credentials.size() <= i)
                {
                    credentials.add(credential);
                    loggedIn = false;
                }
                else
                {
                    if (!credentials.get(i).equals(credential))
                    {
                        credentials.set(i, credential);
                        loggedIn = false;
                    }
                }
            }
            scanSize = Integer.parseInt(sharedPreferences.getString(context.getString(R.string.pref_scan_radius), "1"));
            showIvs = sharedPreferences.getBoolean(context.getString(R.string.pref_show_ivs), false);
            pokeSet = new HashSet<CatchablePokemon>();
            scanPoints = new ArrayList<LatLng>();
            listener = null;
        }

        /**
         * Creates a ScanPokemonTask for <b>foreground</b> scanning
         * @param context the application context
         * @param listener the scan listener. Should not be null.
         */
        public ScanPokemonTask(Context context, ScanListener listener)
        {
            this(context);
            this.listener = listener;
        }

        /**
         * Scans for pokemon.
         * If listener is not null, this method will be called as part of doInBackground from the
         * Asynctask. If listener is null, this method will be called directly from the service's
         * thread.
         * @param latitude the latitude of the position to scan at
         * @param longitude the longitude of the position to scan at
         * @return true if completed without errors
         */
        protected boolean performScan(double latitude, double longitude)
        {
            /* Set up scan points */
            double unit = SCAN_RADIUS / KM_PER_DEGREE;
            for (int i = 0; i <= scanSize; i++)
            {
                double angle = 2*Math.PI / SCAN_PARAMETERS[i][1];
                for (int j = 0; j < SCAN_PARAMETERS[i][1]; j++)
                {
                    double resultant = j*angle;
                    double lat = latitude + Math.cos(resultant) * unit * SCAN_PARAMETERS[i][0];
                    double lng = longitude + Math.sin(resultant) * unit * SCAN_PARAMETERS[i][0];
                    scanPoints.add(new LatLng(lat, lng));
                }
            }
            /** The credentials specified in preferences are rotated during the scan in a round
             * robin fashion. The program pauses for 10.5 secs after each rotation of credentials
             * as there appears to be approximately 10 secs delay on updating of catchable pokemon
             * on the Pokemon GO servers.
             */
            if (credentials.size() == 0)
            {
                error = "Must configure at least 1 set of credentials!";
                return false;
            }
            try
            {
                if (!loggedIn)
                {
                    httpClient = new OkHttpClient();
                    go = new PokemonGo[credentials.size()];
                    for (int i = 0; i < credentials.size(); i++)
                    {
                        Pair<String, String> credential = credentials.get(i);
                        PtcCredentialProvider ptcCredentialProvider =
                                new PtcCredentialProvider(httpClient, credential.first, credential.second);
                        go[i] = new PokemonGo(httpClient);
                        go[i].login(ptcCredentialProvider);
                    }
                }
                scanResult = new ScanResult();
                for (int i = 0; i < scanPoints.size(); i++)
                {
                    ScanResult tempResult = new ScanResult();
                    LatLng location = scanPoints.get(i);
                    int credentialIndex = i % credentials.size();
                    go[credentialIndex].setLocation(location.latitude, location.longitude, 20);
                    List<CatchablePokemon> list = go[credentialIndex].getMap().getCatchablePokemon();
                    tempResult.add(new ScanResult.MarkerParams(location, "Scan target", 0));
                    for (CatchablePokemon pokemon : list)
                    {
                        if (!pokeSet.contains(pokemon))
                        {
                            EncounterResult encounterResult = pokemon.encounterPokemon();
                            String markerText = pokemon.getPokemonId().toString() + " " +
                                    TIME_FORMAT.format(new Date(pokemon.getExpirationTimestampMs()));
                            if (showIvs)
                            {
                                PokemonDataOuterClass.PokemonData pokemonData = encounterResult.getPokemonData();
                                float ivPercent = (pokemonData.getIndividualAttack() +
                                        pokemonData.getIndividualDefense() +
                                        pokemonData.getIndividualStamina()) / 0.45f;
                                markerText += String.format(" [IV:%d%%]", Math.round(ivPercent));
                            }
                            pokeSet.add(pokemon);

                            tempResult.add(new ScanResult.MarkerParams(
                                    pokemon.getLatitude(), pokemon.getLongitude(),
                                    markerText, pokemon.getPokemonId().getNumber()));
                        }
                    }
                    if (listener != null)
                    {
                        publishProgress(tempResult);
                    }
                    scanResult.addAll(tempResult);
                    try
                    {
                        if (credentialIndex == credentials.size() - 1)
                        {
                            Thread.sleep(10500);
                        }
                    } catch (InterruptedException e)
                    {

                    }
                }
            }
            catch (RemoteServerException | AsyncPokemonGoException e)
            {
                e.printStackTrace();
                error = "Remote server error";
                return false;
            }
            catch (LoginFailedException e)
            {
                e.printStackTrace();
                error = "Failed to login. Credentials provided may be invalid or banned.";
                return false;
            }
            if (scanResult.size() == 0)
            {
                error = "Sorry no pokemons found";
                return false;
            }
            return true;
        }

        public ScanResult getResult()
        {
            return scanResult;
        }

        public String getError()
        {
            return error;
        }

        @Override
        protected Boolean doInBackground(Double... coords)
        {
            if (coords.length < 2)
            {
                error = "Unexpected error";
                return false;
            }
            return performScan(coords[0], coords[1]);
        }

        @Override
        protected void onProgressUpdate(ScanResult... result)
        {
            // This method should only be called if listener is not null, but we're checking again
            // just to make sure
            if (listener != null)
            {
                listener.onScanResult(result[0]);
            }
        }

        @Override
        protected void onPostExecute(Boolean result)
        {
            // This method should only be called if listener is not null, but we're checking again
            // just to make sure
            if (listener != null)
            {
                if (result)
                {
                    listener.onScanComplete();
                }
                else
                {
                    listener.onScanError(error);
                }
            }
        }

    }

    private static class SerialExecutor implements Executor
    {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        final Executor executor;
        Runnable active;

        SerialExecutor(Executor executor) {
            this.executor = executor;
        }

        public synchronized void execute(final Runnable r) {
            tasks.add(new Runnable() {
                public void run() {
                    try {
                        r.run();
                    } finally {
                        scheduleNext();
                    }
                }
            });
            if (active == null) {
                scheduleNext();
            }
        }

        protected synchronized void scheduleNext() {
            if ((active = tasks.poll()) != null) {
                executor.execute(active);
            }
        }
    }


}
