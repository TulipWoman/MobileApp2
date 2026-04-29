package com.example.mylocation;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.HashMap;
import java.util.Map;

public class MyLocation extends AppCompatActivity {

    static final String NOTIFICATION_KEY = "MyLocation";
    static final int NOTIFICATION_INTENT_CODE = 0;

    Map<String, StoredLocation> storedLocations = new HashMap<>();
    double triggerDistance = 100;   // 100 metres

    MapView mapView;
    ActivityResultLauncher<String[]> locationPermissionRequest;
    NotificationManagerCompat notificationManager;

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

        notificationManager = NotificationManagerCompat.from(getApplicationContext());
        createNotificationChannel();   // IMPORTANT: channel created BEFORE notifications

        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        MapController mapController = (MapController) mapView.getController();
        mapController.setZoom(15);
        GeoPoint myPoint = new GeoPoint(52.268, -2.150);
        mapController.setCenter(myPoint);

        // Create some interesting locations
        StoredLocation location1 = new StoredLocation("Ombersley", 52.27113, -2.2289);
        StoredLocation location2 = new StoredLocation("City Campus", 52.1958, -2.2261);
        StoredLocation location3 = new StoredLocation("Beer", 50.69713, -3.10145);

        // Enable notifications for all locations initially
        location1.notificationsRequired = true;
        location2.notificationsRequired = true;
        location3.notificationsRequired = true;

        storedLocations.put(location1.locationName, location1);
        storedLocations.put(location2.locationName, location2);
        storedLocations.put(location3.locationName, location3);

        // Create the listener
        locationListener = new LocationListener() {

            @Override
            public void onLocationChanged(@NonNull Location location) {

                Log.d("MyLocation", "New location: " + location);

                GeoPoint currentLocation = new GeoPoint(
                        location.getLatitude(),
                        location.getLongitude()
                );

                for (StoredLocation storedLocation : storedLocations.values()) {

                    GeoPoint geoPoint = new GeoPoint(
                            storedLocation.latitude,
                            storedLocation.longitude
                    );

                    double distance = currentLocation.distanceToAsDouble(geoPoint);

                    if (distance < triggerDistance) {

                        Toast.makeText(
                                getApplicationContext(),
                                "You are " + distance + " metres from " + storedLocation.locationName,
                                Toast.LENGTH_LONG
                        ).show();

                        if (!storedLocation.notificationActive && storedLocation.notificationsRequired) {

                            int notificationID = storedLocation.locationName.hashCode();

                            Notification notification = createNotification(storedLocation, distance);

                            notificationManager.notify(notificationID, notification);

                            storedLocation.notificationActive = true;
                        }
                    } else {
                        storedLocation.notificationActive = false;
                    }
                }
            }
        };

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
                                    updateLocation();
                                } else {
                                    Log.d("MyLocation", "Permission denied");
                                }
                            }
                        }
                );
    }

    private void createNotificationChannel() {
        String channelName = "MyLocationChannel";
        int importance = android.app.NotificationManager.IMPORTANCE_DEFAULT;

        NotificationChannel channel =
                new NotificationChannel(NOTIFICATION_KEY, channelName, importance);

        channel.setDescription("MyLocation updates");

        android.app.NotificationManager manager =
                getSystemService(android.app.NotificationManager.class);

        manager.createNotificationChannel(channel);
    }

    private Notification createNotification(StoredLocation storedLocation, double distance) {

        Intent intent = new Intent(this, MyLocation.class);
        intent.putExtra(NOTIFICATION_KEY, storedLocation.locationName);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                NOTIFICATION_INTENT_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, NOTIFICATION_KEY)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("MyLocation update: " + storedLocation.locationName)
                .setContentText("You are " + distance + " metres from " + storedLocation.locationName)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        String locationName = intent.getStringExtra(NOTIFICATION_KEY);

        for (StoredLocation storedLocation : storedLocations.values()) {
            if (storedLocation.locationName.equals(locationName)) {
                showNotificationDialog(storedLocation);
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        locationPermissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
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

        mapView.onPause();
    }

    private void showNotificationDialog(StoredLocation storedLocation) {

        storedLocation.notificationActive = false;

        new AlertDialog.Builder(this)
                .setTitle(storedLocation.locationName)
                .setMessage("You are close to this location. Do you want to continue to receive notifications for this location in the future?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        storedLocation.notificationsRequired = true;
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        storedLocation.notificationsRequired = false;
                    }
                })
                .create()
                .show();
    }
}

