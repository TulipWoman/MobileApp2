package com.example.mylocation;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

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
import android.widget.EditText;
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

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

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
    FirebaseFirestore db;
    ListenerRegistration listenerRegistration;

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
        db = FirebaseFirestore.getInstance();

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

        CollectionReference collection = db.collection("locations");
        db.collection("sharedLocations");


        WriteBatch batch = db.batch();
        batch.set(collection.document(location1.locationName), location1);
        batch.set(collection.document(location2.locationName), location2);
        batch.set(collection.document(location3.locationName), location3);

        batch.commit()
                .addOnSuccessListener(aVoid -> Log.d("MyLocation", "Successfully stored locations to Firebase"))
                .addOnFailureListener(e -> Log.d("MyLocation", "Failed to store to Firebase"));


//        // Enable notifications for all locations initially
//        location1.notificationsRequired = true;
//        location2.notificationsRequired = true;
//        location3.notificationsRequired = true;

        storedLocations.put(location1.locationName, location1);
        storedLocations.put(location2.locationName, location2);
        storedLocations.put(location3.locationName, location3);


        MapEventsOverlay mapEventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                Log.d("MyLocation", "Tap at " + p.toString());
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                //addNewLocationDialog(p);
                Log.d("MyLocation", "Press at " + p.toString());
                addNewLocationDialog(p);
                return true;
            }
        });

        mapView.getOverlays().add(mapEventsOverlay);

        // Create the listener
        locationListener = new LocationListener() {

            @Override
            public void onLocationChanged(@NonNull Location location) {

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
                        result -> {
                            boolean fineLocationAllowed =
                                    Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));

                            if (fineLocationAllowed) {
                                updateLocation();
                            }
                        }
                );
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

        // Only remove markers, NOT the MapEventsOverlay
        mapView.getOverlays().removeIf(o -> o instanceof Marker);

        // PRIVATE locations
        CollectionReference collection = db.collection("locations");

        listenerRegistration = collection.addSnapshotListener((value, error) -> {

            if (error != null) {
                Log.d("MyLocation", "Listener for changes on server not working");
                return;
            }


            storedLocations.clear();

            for (QueryDocumentSnapshot doc : value) {
                StoredLocation location = doc.toObject(StoredLocation.class);
                storedLocations.put(location.locationName, location);
            }

            drawAllMarkers();
        });

        // SHARED locations
        CollectionReference shared = db.collection("sharedLocations");

        shared.addSnapshotListener((value, error) -> {

            if (error != null) return;

            // ⭐ FIX: do NOT clear here — shared adds on top of private
            for (QueryDocumentSnapshot doc : value) {
                StoredLocation sharedLoc = doc.toObject(StoredLocation.class);
                storedLocations.put(sharedLoc.locationName, sharedLoc);
            }

            drawAllMarkers();
        });
    }


    @Override
    protected void onPause() {
        super.onPause();

        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }

        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }

        mapView.onPause();
    }
    private void drawAllMarkers() {

        // ❗ Only remove markers, NOT the MapEventsOverlay
        mapView.getOverlays().removeIf(o -> o instanceof Marker);

        for (StoredLocation storedLoc : storedLocations.values()) {

            GeoPoint geoPoint = new GeoPoint(storedLoc.latitude, storedLoc.longitude);

            Marker marker = new Marker(mapView);
            marker.setPosition(geoPoint);
            marker.setIcon(getDrawable(R.drawable.current_location));
            marker.setTitle(storedLoc.locationName);

            // Click to share
            marker.setOnMarkerClickListener((m, mapView) -> {
                showShareDialog(storedLoc);
                return true;
            });

            mapView.getOverlays().add(marker);
        }

        mapView.invalidate();
    }

    private void showShareDialog(StoredLocation storedLocation) {

        new AlertDialog.Builder(this)
                .setTitle("Share Location")
                .setMessage("Do you want to share " + storedLocation.locationName + " with all users?")
                .setPositiveButton("Share", (dialog, i) -> {

                    db.collection("sharedLocations")
                            .document(storedLocation.locationName)
                            .set(storedLocation)
                            .addOnSuccessListener(a ->
                                    Toast.makeText(this, "Location shared!", Toast.LENGTH_SHORT).show()
                            )
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to share: " + e.getMessage(), Toast.LENGTH_LONG).show()
                            );

                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }


    private void addNewLocationDialog(GeoPoint geoPoint) {
//Log.d("MyLocation", "Long at ", + GeoPoint);
        EditText locationEditText = new EditText(this);

        new AlertDialog.Builder(this)
                .setTitle("Create New Location")
                .setView(locationEditText)
                .setPositiveButton("Add", (dialogInterface, i) -> {

                    String locationName = locationEditText.getText().toString();

                    StoredLocation newLocation = new StoredLocation(
                            locationName,
                            geoPoint.getLatitude(),
                            geoPoint.getLongitude()
                    );

                    db.collection("locations")
                            .document(newLocation.locationName)
                            .set(newLocation);
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private void createNotificationChannel() {
        NotificationChannel channel =
                new NotificationChannel(NOTIFICATION_KEY, "MyLocationChannel",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT);

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

    private void showNotificationDialog(StoredLocation storedLocation) {

        storedLocation.notificationActive = false;

        new AlertDialog.Builder(this)
                .setTitle(storedLocation.locationName)
                .setMessage("You are close to this location. Do you want to continue to receive notifications for this location in the future?")
                .setPositiveButton("Yes", (dialogInterface, i) -> {
                    storedLocation.notificationsRequired = true;
                    db.collection("locations")
                            .document(storedLocation.locationName)
                            .set(storedLocation);
                })
                .setNegativeButton("No", (dialogInterface, i) -> {
                    storedLocation.notificationsRequired = false;
                    db.collection("locations")
                            .document(storedLocation.locationName)
                            .set(storedLocation);
                })
                .create()
                .show();
    }
}

