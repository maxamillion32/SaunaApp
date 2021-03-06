package fi.jamk.saunaapp.services;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserLocationService implements LocationListener {
    private static final String TAG = "UserLocationService";
    private static LocationRequest mLocationRequest;
    private static List<LocationListener> listenerList;
    private static Location cachedLastLocation;

    private GoogleApiClient apiClient;

    private UserLocationService(GoogleApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public static UserLocationService newInstance(GoogleApiClient apiClient) {
        if (listenerList == null) {
            listenerList = new ArrayList<>();
        }
        return new UserLocationService(apiClient);
    }

    public boolean requestLocationUpdates(Context ctx, LocationListener l) {
        boolean hasPermission = checkPermissionsAndStartLocationListener(ctx);

        if (hasPermission) {
            listenerList.add(l);
        } else {
            Log.e(TAG, "Provided context " + ctx.toString() + " has not the needed user permissions.");
        }

        if (cachedLastLocation != null) {
            l.onLocationChanged(cachedLastLocation);
        }

        return hasPermission;
    }

    public boolean removeListener(LocationListener l) {
        if (listenerList.size() >= 1) {
            mLocationRequest = null;

            if (apiClient.isConnected()) {
                LocationServices.FusedLocationApi.removeLocationUpdates(apiClient, this);
            }
        }
        return listenerList.remove(l);
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, location.toString());
        for (LocationListener l : listenerList) {
            l.onLocationChanged(location);
        }
        cachedLastLocation = location;
    }

    /**
     * Check locations permissions and set location if
     * permissions are granted.
     *
     * @return False if no permissions, true if location set
     */
    private boolean checkPermissionsAndStartLocationListener(Context ctx) {
        if (
                ActivityCompat.checkSelfPermission(
                        ctx, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(
                        ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                ) {
            Log.d(TAG, "No location permissions granted.");
            return false;
        }

        if (mLocationRequest == null) {
            mLocationRequest = LocationRequest.create();

            // Request location updates.
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    this.apiClient, mLocationRequest, this);
        }

        return true;
    }

    public static Location getCachedLocation() { return cachedLastLocation; }
}
