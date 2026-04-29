package com.example.mylocation;

import java.io.Serializable;

public class StoredLocation implements Serializable {
    public String locationName;
    public double latitude;
    public double longitude;
    public boolean notificationActive;
    public boolean notificationsRequired;

    public StoredLocation(){}

    public StoredLocation(String plocName, double pLatitude, double pLongitude){
        locationName = plocName;
        latitude = pLatitude;
        longitude = pLongitude;

        notificationActive = false;
        notificationsRequired = true;
    }
}
