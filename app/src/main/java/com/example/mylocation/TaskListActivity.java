package com.example.mylocation;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class TaskListActivity extends AppCompatActivity {

    private RecyclerView myTasksRecycler;
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
        setContentView(R.layout.activity_task_list);

        // --- My Tasks RecyclerView ---
        myTasksRecycler = findViewById(R.id.myTasksRecycler);
        myTasksRecycler.setLayoutManager(new LinearLayoutManager(this));
        myTasksAdapter = new TaskListAdapter(myTasks, loc -> openTask(loc, "locations"));
        myTasksRecycler.setAdapter(myTasksAdapter);

        // --- Shared Tasks RecyclerView ---
        sharedTasksRecycler = findViewById(R.id.sharedTasksRecycler);
        sharedTasksRecycler.setLayoutManager(new LinearLayoutManager(this));
        sharedTasksAdapter = new TaskListAdapter(sharedTasks, loc -> openTask(loc, "sharedLocations"));
        sharedTasksRecycler.setAdapter(sharedTasksAdapter);

        // --- Add Task button ---
        Button addTaskButton = findViewById(R.id.addTaskButton);
        addTaskButton.setOnClickListener(v -> {
            // Open AddLocationActivity without lat/lon — task-only mode
            Intent intent = new Intent(this, AddLocationActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Listen to personal tasks
        myTasksRegistration = FirebaseFirestore.getInstance()
                .collection("locations")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    myTasks.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        myTasks.add(doc.toObject(StoredLocation.class));
                    }
                    myTasksAdapter.notifyDataSetChanged();
                });

        // Listen to shared tasks
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
}