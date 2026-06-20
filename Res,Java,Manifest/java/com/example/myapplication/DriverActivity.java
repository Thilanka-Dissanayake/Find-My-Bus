package com.example.myapplication;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DriverActivity extends AppCompatActivity implements PassengerAdapter.OnPassengerPickupListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "DriverActivity";

    // Key used to receive the busId from MainActivity
    public static final String EXTRA_BUS_ID = "com.example.myapplication.BUS_ID";

    private TextView tvStatus, tvPassengerListTitle, tvPassengerCount;
    private Button btnStartSharing, btnStopSharing;

    private RecyclerView rvPassengerList;
    private PassengerAdapter passengerAdapter;
    private List<PassengerInfo> acceptedPassengersList;
    private int passengerCount = 0; // Counts passengers marked as picked up

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseReference busRef, bookingRef; // Firebase references
    private ChildEventListener bookingListener; // Listener for booking requests

    private String busId; // Holds the unique ID for this driver's bus
    private boolean isTracking = false; // Tracks if location sharing is active
    private String routeName; // Holds the route name entered at login

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver);

        // Get data passed from MainActivity
        Intent intent = getIntent();
        routeName = intent.getStringExtra(MainActivity.EXTRA_ROUTE);
        busId = intent.getStringExtra(EXTRA_BUS_ID);

        // Set default route if none provided
        if (routeName == null || routeName.isEmpty()) {
            routeName = "No Route Specified";
        }

        // Critical check: Ensure busId was passed correctly
        if (busId == null || busId.isEmpty()) {
            Log.e(TAG, "FATAL ERROR: busId was not passed to DriverActivity.");
            Toast.makeText(this, "Error: No Bus ID found. Cannot operate.", Toast.LENGTH_LONG).show();
            finish(); // Close activity if busId is missing
            return;
        }
        Log.d(TAG, "Driver operating for busId: " + busId);

        // Find UI elements
        tvStatus = findViewById(R.id.tvStatus);
        btnStartSharing = findViewById(R.id.btnStartSharing);
        btnStopSharing = findViewById(R.id.btnStopSharing);
        tvPassengerListTitle = findViewById(R.id.tv_passenger_list_title);
        rvPassengerList = findViewById(R.id.rv_passenger_list);
        tvPassengerCount = findViewById(R.id.tv_passenger_count);

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Set up Firebase references using the dynamic busId
        busRef = FirebaseDatabase.getInstance().getReference("buses").child(busId);
        bookingRef = busRef.child("booking_requests");

        // Set up RecyclerView for accepted passengers
        acceptedPassengersList = new ArrayList<>();
        passengerAdapter = new PassengerAdapter(acceptedPassengersList);
        rvPassengerList.setLayoutManager(new LinearLayoutManager(this));
        rvPassengerList.setAdapter(passengerAdapter);
        passengerAdapter.setOnPassengerPickupListener(this); // Set listener for checkbox clicks

        // Define the location update callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    if (location != null && isTracking) {
                        // Send location data to Firebase
                        busRef.child("location").child("latitude").setValue(location.getLatitude());
                        busRef.child("location").child("longitude").setValue(location.getLongitude());
                        Log.d(TAG, "Location sent: " + location.getLatitude() + ", " + location.getLongitude());
                        // Update UI status
                        String status = "Status: Sharing Location\nLat: " + String.format("%.6f", location.getLatitude()) + "\nLng: " + String.format("%.6f", location.getLongitude());
                        tvStatus.setText(status);
                    }
                }
            }
        };

        // Set button listeners
        btnStartSharing.setOnClickListener(v -> startTracking());
        btnStopSharing.setOnClickListener(v -> stopTracking());
        btnStopSharing.setEnabled(false); // Initially disabled
    }

    // Called when "Start Sharing Location" button is clicked
    private void startTracking() {
        if (!isTracking) {
            // Check for location permissions
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
                // Request permissions if not granted
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE);
                return; // Wait for permission result
            }

            // Permissions granted, proceed with starting tracking

            // Save the route name to Firebase under this busId
            busRef.child("route").setValue(routeName)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Route saved to Firebase: " + routeName))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to save route", e));

            isTracking = true;
            btnStartSharing.setEnabled(false);
            btnStopSharing.setEnabled(true);
            tvStatus.setText("Status: Connecting...");

            // Reset and show the passenger counter
            passengerCount = 0;
            tvPassengerCount.setText("Passengers On-Board: 0");
            tvPassengerCount.setVisibility(View.VISIBLE);

            // Configure location request parameters
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // 5 second interval
                    .setWaitForAccurateLocation(false)
                    .setMinUpdateIntervalMillis(3000) // 3 second fastest update
                    .build();

            // Start requesting location updates
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
            Log.d(TAG, "Location updates requested.");

            // Start listening for incoming booking requests
            listenForBookings();
        }
    }

    // Called when "Stop Sharing Location" button is clicked
    private void stopTracking() {
        if (isTracking) {
            isTracking = false;
            // Stop location updates
            fusedLocationClient.removeLocationUpdates(locationCallback);

            // Update button states and status text
            btnStartSharing.setEnabled(true);
            btnStopSharing.setEnabled(false);
            tvStatus.setText("Status: Not Sharing");
            Toast.makeText(this, "Location sharing stopped", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Location sharing stopped.");

            // Stop listening for booking requests
            if (bookingListener != null) {
                bookingRef.removeEventListener(bookingListener);
                bookingListener = null; // Reset listener
            }

            // Clear the list of accepted passengers and hide the list UI
            acceptedPassengersList.clear();
            passengerAdapter.notifyDataSetChanged();
            rvPassengerList.setVisibility(View.GONE);
            tvPassengerListTitle.setVisibility(View.GONE);

            // Hide and reset the passenger counter
            tvPassengerCount.setVisibility(View.GONE);
            passengerCount = 0;

            // Remove this bus's entire node from Firebase
            busRef.removeValue()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Bus data removed from Firebase."))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to remove bus data.", e));
        }
    }

    // Sets up the listener for new booking requests under this busId
    private void listenForBookings() {
        if (bookingListener == null) {
            bookingListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    // A new booking request appeared
                    Log.d(TAG, "New booking request received: " + dataSnapshot.getKey());

                    // Extract passenger details from the snapshot
                    String name = dataSnapshot.child("passengerName").getValue(String.class);
                    String location = dataSnapshot.child("passengerLocation").getValue(String.class);
                    String contact = dataSnapshot.child("passengerContact").getValue(String.class);
                    String passengerUid = dataSnapshot.child("passengerUid").getValue(String.class);

                    // Ensure details are present
                    if (name == null || location == null || contact == null || passengerUid == null) {
                        Log.e(TAG, "Incomplete booking request received.");
                        dataSnapshot.getRef().removeValue(); // Remove invalid request
                        return;
                    }

                    String passengerDetails = "Name: " + name + "\nPickup: " + location + "\nContact: " + contact;

                    // Show a confirmation dialog to the driver
                    new AlertDialog.Builder(DriverActivity.this)
                            .setTitle("New Passenger Request!")
                            .setMessage(passengerDetails)
                            .setPositiveButton("ACCEPT", (dialog, which) -> {
                                // Driver accepted the request
                                PassengerInfo passenger = new PassengerInfo(name, location, contact);
                                acceptedPassengersList.add(passenger);
                                passengerAdapter.notifyItemInserted(acceptedPassengersList.size() - 1);

                                // Make the passenger list visible if it's the first one
                                if (rvPassengerList.getVisibility() == View.GONE) {
                                    rvPassengerList.setVisibility(View.VISIBLE);
                                    tvPassengerListTitle.setVisibility(View.VISIBLE);
                                }

                                // Send acceptance notification back to the passenger
                                sendAcceptanceToPassenger(passengerUid, routeName);

                                // Remove the processed request from Firebase
                                dataSnapshot.getRef().removeValue();
                            })
                            .setNegativeButton("REJECT", (dialog, which) -> {
                                // Driver rejected the request
                                Log.d(TAG, "Booking request rejected.");
                                // Remove the rejected request from Firebase
                                dataSnapshot.getRef().removeValue();
                            })
                            .setIcon(R.drawable.bus_icon) // Use bus icon in dialog
                            .setCancelable(false) // Driver must choose accept or reject
                            .show();
                }

                // Other ChildEventListener methods (not needed for this logic)
                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {}
                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {}
                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {}
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Booking listener cancelled", databaseError.toException());
                }
            };
            // Attach the listener to the booking requests node
            bookingRef.addChildEventListener(bookingListener);
        }
    }

    // Callback method from PassengerAdapter when a checkbox is ticked
    @Override
    public void onPassengerPickedUp(PassengerInfo passenger, int position) {
        if (!passenger.isPickedUp()) { // Prevent double counting if clicked multiple times
            Log.d(TAG, "Passenger picked up: " + passenger.getName());
            // Mark the passenger as picked up in the local list
            passenger.setPickedUp(true);
            // Increment the on-board counter
            passengerCount++;
            // Update the counter text view
            tvPassengerCount.setText("Passengers On-Board: " + passengerCount);
            // Notify the adapter to refresh the specific item (disables the checkbox)
            passengerAdapter.notifyItemChanged(position);
        }
    }

    // Sends the acceptance notification to the passenger via Firebase
    private void sendAcceptanceToPassenger(String passengerUid, String busRoute) {
        // Reference the specific passenger's booking node
        DatabaseReference passengerBookingRef = FirebaseDatabase.getInstance().getReference("user_bookings")
                .child(passengerUid);

        // Create the acceptance data payload
        HashMap<String, String> acceptanceData = new HashMap<>();
        acceptanceData.put("busId", this.busId); // Include the driver's unique busId
        acceptanceData.put("route", busRoute);
        acceptanceData.put("status", "accepted");

        // Write the data to Firebase
        passengerBookingRef.setValue(acceptanceData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Acceptance notification sent to passenger: " + passengerUid))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send acceptance notification", e));
    }

    // Handles the result of the location permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted
                Log.d(TAG, "Location permission granted by user.");
                startTracking(); // Try starting tracking again
            } else {
                // Permission was denied
                Toast.makeText(this, "Location permission is required to share location.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Ensure tracking stops if the activity is stopped
        stopTracking();
    }
}