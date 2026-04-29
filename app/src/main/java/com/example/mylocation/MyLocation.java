package com.example.mylocation;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

public class MyLocation extends AppCompatActivity {

    MapView mapView;
    ActivityResultLauncher<String[]> locationPermissionRequest;

    long minimumTimeBetweenUpdates = 10000;      // 10 seconds
    float minimumDistanceBetweenUpdates = 0.5f;  // 0.5 metres

    LocationListener locationListener;
    LocationManager locationManager;

    @SuppressLint("MissingPermission")
    public void updateLocation() {

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {

            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minimumTimeBetweenUpdates,
                    minimumDistanceBetweenUpdates,
                    locationListener
            );
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_my_location);

        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        MapController mapController = (MapController) mapView.getController();
        mapController.setZoom(15);
        GeoPoint myPoint = new GeoPoint(52.268, -2.150);
        mapController.setCenter(myPoint);

        // Create the listener (Page 9)
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                Log.d("MyLocation", "New location: " + location);

                GeoPoint currentLocation = new GeoPoint(
                        location.getLatitude(),
                        location.getLongitude()
                );

                Marker marker = new Marker(mapView);
                marker.setPosition(currentLocation);

                mapView.getOverlays().add(marker);
                mapView.invalidate();
            }
        };

        // Permission launcher (Page 8)
        locationPermissionRequest =
                registerForActivityResult(
                        new ActivityResultContracts.RequestMultiplePermissions(),
                        new ActivityResultCallback<Map<String, Boolean>>() {
                            @Override
                            public void onActivityResult(Map<String, Boolean> result) {

                                boolean fineLocationAllowed =
                                        Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));

                                if (fineLocationAllowed) {
                                    Log.d("MyLocation", "Permission granted");
                                    updateLocation();   // Page 9 instruction
                                } else {
                                    Log.d("MyLocation", "Permission denied");
                                }
                            }
                        }
                );
    }

    @Override
    protected void onResume() {
        super.onResume();

        locationPermissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });

        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        if(locationManager != null) locationManager.removeUpdates(locationListener);
        mapView.onPause();
    }
}

