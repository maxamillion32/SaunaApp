package fi.jamk.saunaapp.fragments;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.appinvite.AppInvite;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

import fi.jamk.saunaapp.activities.BaseActivity;
import fi.jamk.saunaapp.activities.MainActivity;
import fi.jamk.saunaapp.R;
import fi.jamk.saunaapp.activities.SaunaDetailsActivity;
import fi.jamk.saunaapp.models.Sauna;
import fi.jamk.saunaapp.services.UserLocationService;

/**
 * A {@link Fragment} subclass that displays
 * nearby Saunas on google map.
 *
 * Use the {@link SaunaMapFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SaunaMapFragment extends Fragment implements
        OnMapReadyCallback, GoogleMap.OnMarkerClickListener,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks,
        LocationListener {
    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";
    private static final String TAG = "SaunaMapFragment";
    private static final float MAP_ZOOM = 12.0f;

    private UserLocationService mUserLocationService;
    private GoogleApiClient mGoogleApiClient;

    // Map center position
    private LatLng userPos;

    private HashMap<String, Marker> markers;
    private HashMap<Marker, Sauna> reverseMarkers;

    private DatabaseReference mFirebaseDatabaseReference;
    private ChildEventListener mFirebaseListener;

    private AdView mAdView;
    private GoogleMap map;
    private MapView saunaMapView;

    public SaunaMapFragment() {
        super();
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param sectionNumber Section number
     *
     * @return A new instance of fragment SaunaMapFragment.
     */
    public static SaunaMapFragment newInstance(int sectionNumber) {
        SaunaMapFragment fragment = new SaunaMapFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGoogleApiClient = new GoogleApiClient.Builder(getContext())
                .addApi(LocationServices.API)
                .build();

        mGoogleApiClient.registerConnectionCallbacks(this);
        mUserLocationService = UserLocationService.newInstance(mGoogleApiClient);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_sauna_map, container, false);

        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
        mFirebaseListener = getFirebaseListener();
        markers = new HashMap<>();
        reverseMarkers = new HashMap<>();

        //mapLocation = savedInstanceState.getParcelable(ARG_MAP_LOCATION);
        saunaMapView = (MapView) rootView.findViewById(R.id.saunaMap);
        saunaMapView.onCreate(savedInstanceState);
        saunaMapView.getMapAsync(this);

        // Attach listener to add markers to map
        mFirebaseDatabaseReference.child(BaseActivity.SAUNAS_CHILD)
                .addChildEventListener(mFirebaseListener);

        mAdView = (AdView) rootView.findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        return rootView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onPause() {
        if (mAdView != null) {
            mAdView.pause();
        }
        saunaMapView.onPause();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdView != null) {
            mAdView.resume();
        }
        saunaMapView.onResume();
    }

    @Override
    public void onStart() {
        mGoogleApiClient.connect();
        saunaMapView.onStart();
        super.onStart();
    }

    @Override
    public void onStop() {
        mGoogleApiClient.disconnect();
        mUserLocationService.removeListener(this);
        saunaMapView.onStop();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (mAdView != null) {
            mAdView.destroy();
        }
        if (mFirebaseListener != null) {
            mFirebaseDatabaseReference.removeEventListener(mFirebaseListener);
            mFirebaseListener = null;
        }

        mGoogleApiClient.unregisterConnectionCallbacks(this);
        mGoogleApiClient = null;

        saunaMapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setOnMarkerClickListener(this);
        saunaMapView.onResume();
    }

    /**
     * Set map to given location
     *
     * @param location
     */
    public void setMapLocation(Location location) {
        if (map == null) {
            return;
        }

        userPos = new LatLng(
                location.getLatitude(),
                location.getLongitude());

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(userPos, MAP_ZOOM));
    }

    /**
     * Enable/disable centering map by device location.
     *
     * @param value
     * @throws SecurityException
     */
    public void setMyLocationEnabled(boolean value)
            throws SecurityException {
        if (map == null) {
            return;
        }

        map.setMyLocationEnabled(value);
        saunaMapView.onResume();
    }

    /**
     * Find {@link Sauna} for marker and launch {@link SaunaDetailsActivity}
     *
     * @param marker {@link Marker} for Google Map
     * @return boolean
     */
    @Override
    public boolean onMarkerClick(Marker marker) {
        Sauna sauna = reverseMarkers.get(marker);

        if (sauna != null) {
            ((MainActivity) getActivity()).startDetailsActivity(sauna);
            return true;
        }

        return false;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (!mUserLocationService.requestLocationUpdates(getContext(), this)) {
            ActivityCompat.requestPermissions(
                    getActivity(),
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    BaseActivity.REQUEST_LOCATION);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "GoogleApi connection suspended: "+i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
    }

    /**
     * Location listener. Sets the current location
     * to Google Map.
     *
     * @param location
     */
    @Override
    public void onLocationChanged(Location location) {
        Log.d("locationChanged", "Location changed from fragment");
        setMyLocationEnabled(true);
        setMapLocation(location);
    }

    /**
     * Callback for location permission request, in case
     * permissions had not been granted.
     *
     * @param requestCode
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == BaseActivity.REQUEST_LOCATION) {
            // Close activity if permissions were not granted on runtime.
            if (!mUserLocationService.requestLocationUpdates(getContext(), this)) {
                getActivity().finish();
            }
        }
    }

    /**
     * Listener adds map markers for Saunas in database
     *
     * @return
     */
    private ChildEventListener getFirebaseListener() {
        return new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Sauna sauna = dataSnapshot.getValue(Sauna.class);
                Marker marker = map.addMarker(new MarkerOptions().position(
                        new LatLng(sauna.getLatitude(), sauna.getLongitude())
                ).title(sauna.getName()));

                markers.put(sauna.getId(), marker);
                reverseMarkers.put(marker, sauna);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                String key = dataSnapshot.getKey();
                // Remove marker from map
                Marker m = markers.get(key);
                reverseMarkers.remove(m);
                m.remove();
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
    }
}
