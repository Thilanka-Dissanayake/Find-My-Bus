package com.example.myapplication;

/**
 * A simple data class to hold information about an accepted passenger.
 */
public class PassengerInfo {
    private String name;
    private String location;
    private String contact;
    private boolean isPickedUp; // --- NEW: To track pickup status ---

    // Constructor updated
    public PassengerInfo(String name, String location, String contact) {
        this.name = name;
        this.location = location;
        this.contact = contact;
        this.isPickedUp = false; // Default to false
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public String getContact() {
        return contact;
    }

    // --- NEW: Getter and Setter for pickup status ---
    public boolean isPickedUp() {
        return isPickedUp;
    }

    public void setPickedUp(boolean pickedUp) {
        isPickedUp = pickedUp;
    }
}