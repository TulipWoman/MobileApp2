package com.example.mylocation;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TimePicker;

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
        Button pickDateButton = findViewById(R.id.pickDateButton);
        Button pickTimeButton = findViewById(R.id.pickTimeButton);

        // Image picker
        ActivityResultLauncher<String> pickImageLauncher =
                registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) {
                        imageUri = uri;
                        imagePreview.setImageURI(uri);
                    }
                });

        selectImageButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        pickDateButton.setOnClickListener(v -> showDatePicker());
        pickTimeButton.setOnClickListener(v -> showTimePicker());

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

                            if (loc.dueDate != null) {
                                ((EditText) findViewById(R.id.taskDueDateView)).setText(loc.dueDate);
                            }
                            if (loc.dueTime != null) {
                                ((EditText) findViewById(R.id.taskDueTimeView)).setText(loc.dueTime);
                            }

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
        EditText titleInput       = findViewById(R.id.titleInput);
        EditText descriptionInput = findViewById(R.id.descriptionInput);
        EditText dateView         = findViewById(R.id.taskDueDateView);
        EditText timeView         = findViewById(R.id.taskDueTimeView);

        String title       = titleInput.getText().toString();
        String description = descriptionInput.getText().toString();
        String image       = (imageUri != null) ? imageUri.toString() : null;
        String dueDate     = dateView.getText().toString();
        String dueTime     = timeView.getText().toString();

        // Use existing ID if editing
        String id = isEdit ? editId : UUID.randomUUID().toString();

        // Create object
        StoredLocation loc = new StoredLocation(
                id, title, description, image, lat, lon
        );
        loc.dueDate = dueDate.isEmpty() ? null : dueDate;
        loc.dueTime = dueTime.isEmpty() ? null : dueTime;

        // Save to Firestore
        FirebaseFirestore.getInstance()
                .collection("locations")
                .document(id)
                .set(loc)
                .addOnSuccessListener(a -> finish());
    }

    private void showDatePicker() {
        Log.d("AddLocationActivity", "showDatePicker");

        java.util.Calendar cal = java.util.Calendar.getInstance();
        int year  = cal.get(java.util.Calendar.YEAR);
        int month = cal.get(java.util.Calendar.MONTH);
        int day   = cal.get(java.util.Calendar.DAY_OF_MONTH);

        DatePickerDialog.OnDateSetListener listener = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                EditText dateView = findViewById(R.id.taskDueDateView);
                // month is 0-based, so add 1 for display
                dateView.setText(dayOfMonth + "/" + (month + 1) + "/" + year);
            }
        };

        new DatePickerDialog(this, listener, year, month, day).show();
    }

    private void showTimePicker() {
        Log.d("AddLocationActivity", "showTimePicker");

        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour   = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = cal.get(java.util.Calendar.MINUTE);

        TimePickerDialog.OnTimeSetListener listener = new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                EditText timeView = findViewById(R.id.taskDueTimeView);
                // Zero-pad minutes so "9:05" doesn't show as "9/5"
                timeView.setText(String.format("%02d:%02d", hourOfDay, minute));
            }
        };

        // true = 24-hour clock
        new TimePickerDialog(this, listener, hour, minute, true).show();
    }
}