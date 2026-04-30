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
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

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
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class MyLocation extends AppCompatActivity {

    static final String NOTIFICATION_KEY         = "MyLocation";
    static final int    NOTIFICATION_INTENT_CODE = 0;

    Map<String, StoredLocation> storedLocations = new HashMap<>();
    // Tracks which location IDs have an active notification this session.
    // Kept separately so Firestore snapshot refreshes don't reset the guard.
    final java.util.Set<String> activeNotifications = new java.util.HashSet<>();
    double triggerDistance = 100;

    MapView mapView;
    Marker  currentLocationMarker;
    ActivityResultLauncher<String[]> locationPermissionRequest;
    NotificationManagerCompat notificationManager;

    long  minimumTimeBetweenUpdates     = 10000;
    float minimumDistanceBetweenUpdates = 0.5f;

    LocationListener locationListener;
    LocationManager  locationManager;
    FirebaseFirestore db;

    // Class-level so onPause can remove them (fixes Bug #2)
    ListenerRegistration locationsRegistration;
    ListenerRegistration sharedRegistration;

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
        createNotificationChannel();

        // Map setup
        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        MapController mapController = (MapController) mapView.getController();
        mapController.setZoom(15);
        mapController.setCenter(new GeoPoint(52.268, -2.150));

        // Task List button — navigate to the list screen
        Button taskListButton = findViewById(R.id.taskListButton);
        taskListButton.setOnClickListener(v ->
                startActivity(new Intent(this, TaskListActivity.class))
        );

        // Long-press on map opens AddLocationActivity with lat/lon
        MapEventsOverlay mapEventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) { return false; }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                Log.d("MyLocation", "Long press at " + p);
                Intent intent = new Intent(MyLocation.this, AddLocationActivity.class);
                intent.putExtra("lat", p.getLatitude());
                intent.putExtra("lon", p.getLongitude());
                startActivity(intent);
                return true;
            }
        });
        mapView.getOverlays().add(mapEventsOverlay);

        // Location listener
        locationListener = new LocationListener() {
            boolean firstFix = true;

            @Override
            public void onLocationChanged(@NonNull Location location) {
                GeoPoint current = new GeoPoint(location.getLatitude(), location.getLongitude());

                // Move or create the "you are here" marker
                if (currentLocationMarker == null) {
                    currentLocationMarker = new Marker(mapView);
                    currentLocationMarker.setTitle("You are here");
                    currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    mapView.getOverlays().add(currentLocationMarker);
                }
                currentLocationMarker.setPosition(current);

                // Pan to current position on the first GPS fix
                if (firstFix) {
                    mapView.getController().animateTo(current);
                    firstFix = false;
                }

                mapView.invalidate();
                for (StoredLocation storedLoc : storedLocations.values()) {
                    GeoPoint target   = new GeoPoint(storedLoc.latitude, storedLoc.longitude);
                    double   distance = current.distanceToAsDouble(target);
                    if (distance < triggerDistance) {
                        Toast.makeText(getApplicationContext(),
                                "You are " + (int) distance + " m from " + storedLoc.locationName,
                                Toast.LENGTH_LONG).show();
                        if (!activeNotifications.contains(storedLoc.id) && storedLoc.notificationsRequired) {
                            notificationManager.notify(
                                    storedLoc.locationName.hashCode(),
                                    createNotification(storedLoc, distance));
                            activeNotifications.add(storedLoc.id);
                        }
                    } else {
                        activeNotifications.remove(storedLoc.id);
                    }
                }
            }
        };

        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))) {
                        updateLocation();
                    }
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Only prompt for permissions if not already granted; otherwise start location updates directly
        boolean hasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean hasNotif = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!hasFine || !hasNotif) {
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            updateLocation();
        }

        mapView.onResume();

        // Assign to class fields so onPause can remove them
        locationsRegistration = FirebaseFirestore.getInstance()
                .collection("locations")
                .addSnapshotListener((value, error) -> {
                    if (error != null) { Log.d("MyLocation", "listener error: " + error); return; }
                    storedLocations.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        StoredLocation loc = doc.toObject(StoredLocation.class);
                        storedLocations.put(loc.id, loc);
                    }
                    drawAllMarkers();
                });

        sharedRegistration = db.collection("sharedLocations")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    mapView.getOverlays().removeIf(o -> o instanceof Marker && o != currentLocationMarker);
                    drawAllMarkers();
                    for (QueryDocumentSnapshot doc : value) {
                        addMarker(doc.toObject(StoredLocation.class), "sharedLocations");
                    }
                    mapView.invalidate();
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationManager       != null) locationManager.removeUpdates(locationListener);
        if (locationsRegistration != null) locationsRegistration.remove();
        if (sharedRegistration    != null) sharedRegistration.remove();
        mapView.onPause();
    }

    private void drawAllMarkers() {
        mapView.getOverlays().removeIf(o -> o instanceof Marker && o != currentLocationMarker);
        for (StoredLocation loc : storedLocations.values()) addMarker(loc, "locations");
        mapView.invalidate();
    }

    private void addMarker(StoredLocation loc, String collection) {
        // Skip tasks with no location attached (saved from task list without coordinates)
        if (loc.latitude == 0 && loc.longitude == 0) return;
        Marker marker = new Marker(mapView);
        marker.setPosition(new GeoPoint(loc.latitude, loc.longitude));
        marker.setIcon(getDrawable(R.drawable.current_location));
        marker.setTitle(loc.locationName);
        marker.setSubDescription(loc.description);
        marker.setOnMarkerClickListener((m, mv) -> { openLocationDetails(loc, collection); return true; });
        mapView.getOverlays().add(marker);
    }

    private void openLocationDetails(StoredLocation loc, String collection) {
        Intent intent = new Intent(MyLocation.this, ViewLocationActivity.class);
        intent.putExtra("id", loc.id);
        intent.putExtra("collection", collection);
        startActivity(intent);
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                NOTIFICATION_KEY, "MyLocationChannel",
                android.app.NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("MyLocation updates");
        getSystemService(android.app.NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification createNotification(StoredLocation loc, double distance) {
        PendingIntent pi = PendingIntent.getActivity(this, loc.id.hashCode(),
                new Intent(this, MyLocation.class).putExtra(NOTIFICATION_KEY, loc.locationName),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, NOTIFICATION_KEY)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("MyLocation: " + loc.locationName)
                .setContentText("You are " + (int) distance + " m away")
                .setContentIntent(pi).setAutoCancel(true).build();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String name = intent.getStringExtra(NOTIFICATION_KEY);
        for (StoredLocation loc : storedLocations.values()) {
            if (loc.locationName.equals(name)) { showNotificationDialog(loc); return; }
        }
    }

    private void showNotificationDialog(StoredLocation loc) {
        activeNotifications.remove(loc.id);
        new android.app.AlertDialog.Builder(this)
                .setTitle(loc.locationName)
                .setMessage("You are close. Keep receiving notifications here?")
                .setPositiveButton("Yes", (d, i) -> {
                    loc.notificationsRequired = true;
                    db.collection("locations").document(loc.id).set(loc);
                })
                .setNegativeButton("No", (d, i) -> {
                    loc.notificationsRequired = false;
                    db.collection("locations").document(loc.id).set(loc);
                }).show();
    }
}