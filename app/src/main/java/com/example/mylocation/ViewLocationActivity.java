package com.example.mylocation;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

public class ViewLocationActivity extends AppCompatActivity {

    private StoredLocation loc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_location);

        String id         = getIntent().getStringExtra("id");
        String collection = getIntent().getStringExtra("collection");
        if (collection == null) collection = "locations";

        boolean isShared = "sharedLocations".equals(collection);

        FirebaseFirestore.getInstance()
                .collection(collection)
                .document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    loc = doc.toObject(StoredLocation.class);
                    if (loc != null) showDetails(isShared);
                });
    }

    private void showDetails(boolean isShared) {
        TextView title       = findViewById(R.id.titleText);
        TextView description = findViewById(R.id.descriptionText);
        TextView dateText    = findViewById(R.id.dateText);
        TextView timeText    = findViewById(R.id.timeText);
        ImageView image      = findViewById(R.id.imageView);
        Button edit          = findViewById(R.id.editButton);
        Button delete        = findViewById(R.id.deleteButton);
        Button share         = findViewById(R.id.shareButton);

        title.setText(loc.locationName);
        description.setText(loc.description);

        // Show date/time if set
        if (loc.dueDate != null && !loc.dueDate.isEmpty()) {
            dateText.setText("Due: " + loc.dueDate);
            dateText.setVisibility(View.VISIBLE);
        }
        if (loc.dueTime != null && !loc.dueTime.isEmpty()) {
            timeText.setText("Time: " + loc.dueTime);
            timeText.setVisibility(View.VISIBLE);
        }

        if (loc.imageUri != null) {
            image.setImageURI(Uri.parse(loc.imageUri));
        }

        if (isShared) {
            edit.setVisibility(View.GONE);
            delete.setVisibility(View.GONE);
            share.setText("Already Shared");
            share.setEnabled(false);
        } else {
            edit.setOnClickListener(v -> editLocation());
            delete.setOnClickListener(v -> deleteLocation());
            share.setOnClickListener(v -> shareLocationToFirestore());
        }
    }

    private void editLocation() {
        Intent intent = new Intent(this, AddLocationActivity.class);
        intent.putExtra("id", loc.id);
        intent.putExtra("lat", loc.latitude);
        intent.putExtra("lon", loc.longitude);
        startActivity(intent);
    }

    private void deleteLocation() {
        FirebaseFirestore.getInstance()
                .collection("locations")
                .document(loc.id)
                .delete()
                .addOnSuccessListener(a -> finish());
    }

    private void shareLocationToFirestore() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Share Location")
                .setMessage("Share \"" + loc.locationName + "\" with all users?")
                .setPositiveButton("Share", (d, i) ->
                        FirebaseFirestore.getInstance()
                                .collection("sharedLocations")
                                .document(loc.id)
                                .set(loc)
                                .addOnSuccessListener(a ->
                                        Toast.makeText(this, "Location shared!", Toast.LENGTH_SHORT).show()
                                )
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                                )
                )
                .setNegativeButton("Cancel", null)
                .show();
    }
}