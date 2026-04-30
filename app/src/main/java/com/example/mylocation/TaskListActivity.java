package com.example.mylocation;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class TaskListActivity extends AppCompatActivity { //personal and shared lists

    private RecyclerView myTasksRecycler; //two views vertically stacked
    private RecyclerView sharedTasksRecycler;

    private TaskListAdapter myTasksAdapter;
    private TaskListAdapter sharedTasksAdapter;

    private final List<StoredLocation> myTasks     = new ArrayList<>();
    private final List<StoredLocation> sharedTasks = new ArrayList<>();

    private ListenerRegistration myTasksRegistration;
    private ListenerRegistration sharedTasksRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_list);//sets linear

        //My Tasks RecyclerView
        myTasksRecycler = findViewById(R.id.myTasksRecycler);
        myTasksRecycler.setLayoutManager(new LinearLayoutManager(this));
        myTasksAdapter = new TaskListAdapter(myTasks,
                loc -> openTask(loc, "locations"),
                loc -> confirmSaveLocation(loc));
        myTasksRecycler.setAdapter(myTasksAdapter);

        //Shared Tasks RecyclerView
        sharedTasksRecycler = findViewById(R.id.sharedTasksRecycler);
        sharedTasksRecycler.setLayoutManager(new LinearLayoutManager(this));
        sharedTasksAdapter = new TaskListAdapter(sharedTasks,
                loc -> openTask(loc, "sharedLocations"),
                loc -> Toast.makeText(this, "Cannot edit shared tasks", Toast.LENGTH_SHORT).show());
        sharedTasksRecycler.setAdapter(sharedTasksAdapter);

        //task button
        Button addTaskButton = findViewById(R.id.addTaskButton);
        addTaskButton.setOnClickListener(v -> promptAddTaskWithLocation());
    }

    @Override
    protected void onResume() {//populates filestore
        super.onResume();

        // Takes data to personal tasks
        myTasksRegistration = FirebaseFirestore.getInstance()
                .collection("locations")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    myTasks.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        myTasks.add(doc.toObject(StoredLocation.class));
                    }
                    myTasksAdapter.notifyDataSetChanged(); //refreshes whole list
                });

        // Takes data to shared tasks
        sharedTasksRegistration = FirebaseFirestore.getInstance()
                .collection("sharedLocations")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    sharedTasks.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        sharedTasks.add(doc.toObject(StoredLocation.class));
                    }
                    sharedTasksAdapter.notifyDataSetChanged();
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (myTasksRegistration != null)     myTasksRegistration.remove();
        if (sharedTasksRegistration != null) sharedTasksRegistration.remove();
    }

    private void openTask(StoredLocation loc, String collection) {
        Intent intent = new Intent(this, ViewLocationActivity.class);
        intent.putExtra("id", loc.id);
        intent.putExtra("collection", collection);
        startActivity(intent);
    }


    @SuppressLint("MissingPermission")
    private void confirmSaveLocation(StoredLocation loc) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Save location to task?")//gps confirm dialogue
                .setMessage("Attach your current GPS location to \"" + loc.locationName + "\"?")
                .setPositiveButton("Save", (d, i) -> {
                    LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                    Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    Location last = gps != null ? gps : net;
                    if (last == null) {
                        Toast.makeText(this, "Could not get current location", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    loc.latitude  = last.getLatitude();
                    loc.longitude = last.getLongitude();
                    FirebaseFirestore.getInstance()
                            .collection("locations")
                            .document(loc.id)
                            .set(loc)
                            .addOnSuccessListener(a ->
                                    Toast.makeText(this, "Location saved to task!", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Dialog shown when the user taps "+ Add Task" — offers to attach current location. */
    @SuppressLint("MissingPermission")
    private void promptAddTaskWithLocation() {//shows add button pressed
        new android.app.AlertDialog.Builder(this)
                .setTitle("Add Task")
                .setMessage("Attach your current location to the new task?")
                .setPositiveButton("Yes, use my location", (d, i) -> {
                    LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                    Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);//fill coords
                    Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    Location last = gps != null ? gps : net;
                    Intent intent = new Intent(this, AddLocationActivity.class);
                    if (last != null) {
                        intent.putExtra("lat", last.getLatitude());
                        intent.putExtra("lon",  last.getLongitude());
                    } else {
                        Toast.makeText(this, "Could not get location — task saved without coordinates", Toast.LENGTH_SHORT).show();
                    }
                    startActivity(intent);
                })
                .setNegativeButton("No location", (d, i) ->
                        startActivity(new Intent(this, AddLocationActivity.class)))
                .show();
    }}