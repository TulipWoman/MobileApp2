package com.example.mylocation;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

public class ViewLocationActivity extends AppCompatActivity {

    private StoredLocation loc;   // the reminder we are viewing

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_location);

        // Get the ID and collection name passed from the marker click.
        // Falls back to "locations" so existing code paths are unaffected.
        String id = getIntent().getStringExtra("id");
        String collection = getIntent().getStringExtra("collection");
        if (collection == null) collection = "locations";

        boolean isShared = "sharedLocations".equals(collection);

        // Load the location from whichever Firestore collection it lives in
        FirebaseFirestore.getInstance()
                .collection(collection)
                .document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    loc = doc.toObject(StoredLocation.class);
                    if (loc != null) {
                        showDetails(isShared);
                    }
                });
    }

    private void showDetails(boolean isShared) {

        TextView title = findViewById(R.id.titleText);
        TextView description = findViewById(R.id.descriptionText);
        ImageView image = findViewById(R.id.imageView);

        Button edit = findViewById(R.id.editButton);
        Button delete = findViewById(R.id.deleteButton);
        Button share = findViewById(R.id.shareButton);

        // Fill UI with reminder data
        title.setText(loc.locationName);
        description.setText(loc.description);

        if (loc.imageUri != null) {
            image.setImageURI(Uri.parse(loc.imageUri));
        }

        if (isShared) {
            // Shared locations are read-only — hide Edit and Delete
            edit.setVisibility(android.view.View.GONE);
            delete.setVisibility(android.view.View.GONE);
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
        intent.putExtra("id", loc.id);   // tells AddLocationActivity to load + edit
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
                                        android.widget.Toast.makeText(this, "Location shared!", android.widget.Toast.LENGTH_SHORT).show()
                                )
                                .addOnFailureListener(e ->
                                        android.widget.Toast.makeText(this, "Failed to share: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show()
                                )
                )
                .setNegativeButton("Cancel", null)
                .show();
    }
}