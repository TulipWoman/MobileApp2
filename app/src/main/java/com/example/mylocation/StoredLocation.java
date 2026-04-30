package com.example.mylocation;

import java.io.Serializable;

public class StoredLocation implements Serializable {

    public String id;               // unique ID for editing/deleting
    public String locationName;     // title of the reminder
    public String description;      // reminder description
    public String imageUri;         // photo URI

    public double latitude;
    public double longitude;

    public boolean notificationActive;
    public boolean notificationsRequired;

    public StoredLocation() {
    }

    public StoredLocation(String id, String locationName, String description,
                          String imageUri, double latitude, double longitude) {
        this.id = id;
        this.locationName = locationName;
        this.description = description;
        this.imageUri = imageUri;
        this.latitude = latitude;
        this.longitude = longitude;
        this.notificationActive = false;
        this.notificationsRequired = true;
    }

    // Keep the short constructor too:
    public StoredLocation(String locationName, double latitude, double longitude) {
        this.id = locationName;
        this.locationName = locationName;
        this.description = "";
        this.imageUri = null;
        this.latitude = latitude;
        this.longitude = longitude;
        this.notificationActive = false;
        this.notificationsRequired = true;
    }
}
