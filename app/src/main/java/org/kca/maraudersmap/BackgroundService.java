package org.kca.maraudersmap;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

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

    public BackgroundService()
    {
        super("BackgroundService");
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
                handleActionLocationUpdated(latitude, longitude);
            }
        }
    }

    /**
     * Handle the location updated action
     * @param latitude the updated latitude
     * @param longitude the updated longitude
     */
    private void handleActionLocationUpdated(double latitude, double longitude)
    {
        // TODO: Handle action Foo
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
