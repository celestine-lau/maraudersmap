package org.kca.maraudersmap;

/**
 * A listener for Pokemon scan results. A ScanListener can be registered with BackgroundService
 * to immediately be notified of the results of a Pokemon Scan
 * Created by ctheng on 1/9/2016.
 */
public interface ScanListener
{
    /**
     * Called when there is a scan result
     * @param result the scan result
     */
    public void onScanResult(ScanResult result);

    /**
     * Called when the scan has an error. It is guaranteed that for each scan, either this
     * method or onScanComplete will be called once.
     * @param errorMessage the scan error
     */
    public void onScanError(String errorMessage);

    /**
     * Called when the scan completes successfully. It is guaranteed that for each scan, either
     * this method or onScanError will be called once
     */
    public void onScanComplete();
}
