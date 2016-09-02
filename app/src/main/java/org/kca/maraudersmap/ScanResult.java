package org.kca.maraudersmap;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Class that contains the results of a Pokemon scan
 * Created by ctheng on 1/9/2016.
 */
public class ScanResult implements Iterable<ScanResult.MarkerParams>, Parcelable
{
    private Set<MarkerParams> resultSet;

    /**
     * Creates a new ScanResult object
     */
    public ScanResult()
    {
        resultSet = new HashSet<ScanResult.MarkerParams>();
    }

    /**
     * Creates a new ScanResult object from a Parcel
     * @param source the Parcel to create from
     */
    public ScanResult(Parcel source)
    {
        this();
        ArrayList<MarkerParams> list = (ArrayList<MarkerParams>)source.readSerializable();
        resultSet.addAll(list);
    }

    /**
     * Adds a Marker parameters to the result
     * @param markerParams the marker parameters to add
     */
    public void add(MarkerParams markerParams)
    {
        resultSet.add(markerParams);
    }

    /**
     * Adds all markers in another scan result to the result
     * @param scanResult the scan result to add
     */
    public void addAll(ScanResult scanResult)
    {
        for (MarkerParams markerParams : scanResult)
        {
            resultSet.add(markerParams);
        }
    }

    /**
     * Clears the current scan result of all results
     */
    public void clear()
    {
        resultSet.clear();
    }

    /**
     * Gets the number of markers in the scan result
     * @return the number of markers
     */
    public int size()
    {
        return resultSet.size();
    }

    @Override
    public Iterator<ScanResult.MarkerParams> iterator()
    {
        return resultSet.iterator();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        ArrayList<MarkerParams> list = new ArrayList<MarkerParams>();
        list.addAll(resultSet);
        dest.writeSerializable(list);
    }

    public static final Parcelable.Creator<ScanResult> CREATOR =
            new Parcelable.Creator<ScanResult>() {

                @Override
                public ScanResult createFromParcel(Parcel source)
                {
                    return new ScanResult(source);
                }

                @Override
                public ScanResult[] newArray(int size)
                {
                    return new ScanResult[size];
                }
            };


    /**
     * Parameters for placing a marker on the map
     */
    public static class MarkerParams implements Serializable
    {
        /** The position on the map */
        double latitude;
        double longitude;
        /** The title text to show */
        String text;
        /** The type of marker: 0 for a scan marker, 1-150 for a pokemon, corresponding to its id */
        int type;

        /**
         * Creates a new MarkerParams with the specified parameters
         * @param latitude the latitude of the marker
         * @param longitude the longitude of the marker
         * @param text the text to show on the marker when clicked
         * @param type the type of marker
         */
        public MarkerParams(double latitude, double longitude, String text, int type)
        {
            this.latitude = latitude;
            this.longitude = longitude;
            this.text = text;
            this.type = type;
        }

        /**
         * Creates a new MarkerParams with the specified parameters
         * @param location the position of the marker
         * @param text the text to show on the marker when clicked
         * @param type the type of marker
         */
        public MarkerParams(LatLng location, String text, int type)
        {
            this(location.latitude, location.longitude, text, type);
        }
    }
}
