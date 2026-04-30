package com.example.mylocation;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.UUID;

public class AddLocationActivity extends AppCompatActivity {

    private Uri imageUri;   // class variable

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_location);

        // Insets padding only
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // UI elements
        EditText titleInput = findViewById(R.id.titleInput);
        EditText descriptionInput = findViewById(R.id.descriptionInput);
        ImageView imagePreview = findViewById(R.id.imagePreview);
        Button selectImageButton = findViewById(R.id.selectImageButton);
        Button saveButton = findViewById(R.id.saveButton);

        // Image picker
        ActivityResultLauncher<String> pickImageLauncher =
                registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) {
                        imageUri = uri;
                        imagePreview.setImageURI(uri);
                    }
                });

        selectImageButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // Detect edit mode
        String editId = getIntent().getStringExtra("id");
        boolean isEdit = editId != null;

        // If editing, load existing data
        if (isEdit) {
            FirebaseFirestore.getInstance()
                    .collection("locations")
                    .document(editId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        StoredLocation loc = doc.toObject(StoredLocation.class);
                        if (loc != null) {
                            titleInput.setText(loc.locationName);
                            descriptionInput.setText(loc.description);

                            if (loc.imageUri != null) {
                                imageUri = Uri.parse(loc.imageUri);
                                imagePreview.setImageURI(imageUri);
                            }
                        }
                    });
        }

        saveButton.setOnClickListener(v -> saveLocation());
    }

    private void saveLocation() {

        // Detect edit mode
        String editId = getIntent().getStringExtra("id");
        boolean isEdit = editId != null;

        // Get lat/lon
        double lat = getIntent().getDoubleExtra("lat", 0);
        double lon = getIntent().getDoubleExtra("lon", 0);

        // UI values
        EditText titleInput = findViewById(R.id.titleInput);
        EditText descriptionInput = findViewById(R.id.descriptionInput);

        String title = titleInput.getText().toString();
        String description = descriptionInput.getText().toString();
        String image = (imageUri != null) ? imageUri.toString() : null;

        // Use existing ID if editing
        String id = isEdit ? editId : UUID.randomUUID().toString();

        // Create object
        StoredLocation loc = new StoredLocation(
                id,
                title,
                description,
                image,
                lat,
                lon
        );

        // Save to Firestore
        FirebaseFirestore.getInstance()
                .collection("locations")
                .document(id)
                .set(loc)
                .addOnSuccessListener(a -> finish());
    }
}
