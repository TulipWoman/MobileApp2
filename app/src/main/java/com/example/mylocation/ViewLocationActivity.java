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

        // Get the ID passed from the marker click
        String id = getIntent().getStringExtra("id");

        // Load the reminder from Firestore
        FirebaseFirestore.getInstance()
                .collection("locations")
                .document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    loc = doc.toObject(StoredLocation.class);
                    if (loc != null) {
                        showDetails();
                    }
                });
    }

    private void showDetails() {

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

        // Button actions
        edit.setOnClickListener(v -> editLocation());
        delete.setOnClickListener(v -> deleteLocation());
        share.setOnClickListener(v -> shareLocation());
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

    private void shareLocation() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");

        String text =
                loc.locationName + "\n" +
                        loc.description + "\n" +
                        "Lat: " + loc.latitude + ", Lon: " + loc.longitude;

        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, "Share via"));
    }
}
