package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

public class PassengerActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private DatabaseReference busesRef;
    private static final String TAG = "PassengerActivity";

    private HashMap<String, Marker> busMarkers;
    private View markerLayout;
    private TextView tvMarkerRoute;
    private ImageView ivMarkerIcon;

    // --- For notifications ---
    private TextView tvNotificationBar;
    private DatabaseReference passengerBookingRef;
    private ValueEventListener bookingListener;
    private String acceptedBusId = null; // Stores the ID of the bus that accepted the ride

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger);

        Log.d(TAG, "onCreate: Activity created.");
        busMarkers = new HashMap<>();

        // Find the notification bar view
        tvNotificationBar = findViewById(R.id.tv_notification_bar);

        // Inflate the custom marker layout from XML
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        try {
            markerLayout = inflater.inflate(R.layout.custom_bus_marker, null);
            tvMarkerRoute = markerLayout.findViewById(R.id.tv_marker_route);
            ivMarkerIcon = markerLayout.findViewById(R.id.iv_marker_icon);
            ivMarkerIcon.setImageResource(R.drawable.bus_icon); // Set the default icon image
        } catch (Exception e) {
            Log.e(TAG, "Error inflating custom_bus_marker.xml. Did you create the file in res/layout?", e);
            Toast.makeText(this, "CRITICAL ERROR: Missing layout file 'custom_bus_marker.xml'", Toast.LENGTH_LONG).show();
            // Consider finishing the activity or handling this error appropriately
            return;
        }

        // Initialize the map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.e(TAG, "Error: SupportMapFragment is null. Check activity_passenger.xml layout file.");
        }

        // Reference the main "buses" node in Firebase
        busesRef = FirebaseDatabase.getInstance().getReference("buses");

        // Start listening for booking acceptance specific to this passenger
        listenForBookingAcceptance();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        Log.d(TAG, "onMapReady: Map is ready.");

        // Set initial map position (e.g., Colombo)
        LatLng colombo = new LatLng(6.9271, 79.8612);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(colombo, 12));

        // Set the listener for clicks on bus markers
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(@NonNull Marker marker) {
                // Retrieve the busId stored in the marker's tag
                String busId = (String) marker.getTag();
                if (busId != null) {
                    // Show the booking bottom sheet, passing the busId
                    BookingBottomSheetFragment bottomSheet = BookingBottomSheetFragment.newInstance(busId);
                    bottomSheet.show(getSupportFragmentManager(), "BookingBottomSheet");
                }
                return true; // Consume the click event
            }
        });

        // Start listening for changes to any bus under the "buses" node
        startBusListener();
    }

    // Listens for drivers accepting this passenger's booking request
    private void listenForBookingAcceptance() {
        FirebaseUser passengerUser = FirebaseAuth.getInstance().getCurrentUser();
        if (passengerUser == null) {
            Log.w(TAG, "listenForBookingAcceptance: Passenger not logged in.");
            return; // Not logged in
        }

        String passengerUid = passengerUser.getUid();
        passengerBookingRef = FirebaseDatabase.getInstance().getReference("user_bookings").child(passengerUid);

        // Clear any old acceptance data when the activity starts
        passengerBookingRef.removeValue()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Cleared previous booking acceptance state."))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to clear previous booking state.", e));

        bookingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // An acceptance notification exists for this passenger
                    String route = dataSnapshot.child("route").getValue(String.class);
                    String busId = dataSnapshot.child("busId").getValue(String.class);
                    Log.d(TAG, "Booking accepted by busId: " + busId + " for route: " + route);

                    // Show the notification bar
                    String notificationText = route + " Bus is Accepted You. The Bus will pickup you soonly";
                    tvNotificationBar.setText(notificationText);
                    tvNotificationBar.setVisibility(View.VISIBLE);

                    // Store the ID of the accepted bus
                    acceptedBusId = busId;

                } else {
                    // No acceptance data found (or it was cleared/removed)
                    Log.d(TAG, "No active booking acceptance found.");
                    tvNotificationBar.setVisibility(View.GONE);
                    acceptedBusId = null;
                }
                // Force all markers to redraw to apply/remove highlighting
                redrawAllMarkers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Booking acceptance listener cancelled", databaseError.toException());
            }
        };
        // Attach the listener to listen continuously
        passengerBookingRef.addValueEventListener(bookingListener);
    }

    // Starts the listener for bus additions, changes, and removals
    private void startBusListener() {
        busesRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                Log.d(TAG, "Bus added: " + dataSnapshot.getKey());
                updateBusMarker(dataSnapshot);
            }
            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                Log.d(TAG, "Bus changed: " + dataSnapshot.getKey());
                updateBusMarker(dataSnapshot);
            }
            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Bus removed: " + dataSnapshot.getKey());
                String busId = dataSnapshot.getKey();
                if (busId != null && busMarkers.containsKey(busId)) {
                    // Remove marker from map and tracking map
                    busMarkers.get(busId).remove();
                    busMarkers.remove(busId);
                }
            }
            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {} // Not needed for this app
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Bus listener error: " + databaseError.getMessage());
            }
        });
    }

    // Creates or updates a bus marker on the map
    private void updateBusMarker(DataSnapshot dataSnapshot) {
        String busId = dataSnapshot.getKey();
        if (busId == null) return;

        // Extract bus data from Firebase snapshot
        String route = dataSnapshot.child("route").getValue(String.class);
        DataSnapshot locationSnapshot = dataSnapshot.child("location");
        Double latitude = locationSnapshot.child("latitude").getValue(Double.class);
        Double longitude = locationSnapshot.child("longitude").getValue(Double.class);

        // Ensure all required data is present
        if (route == null || latitude == null || longitude == null) {
            Log.w(TAG, "Incomplete data for bus: " + busId);
            // Optionally remove the marker if data becomes incomplete
            if (busMarkers.containsKey(busId)) {
                busMarkers.get(busId).remove();
                busMarkers.remove(busId);
            }
            return;
        }

        LatLng busLatLng = new LatLng(latitude, longitude);

        // Check if this bus is the one that accepted the current passenger
        boolean isAccepted = busId.equals(acceptedBusId);

        // Create the custom marker icon (normal or highlighted)
        BitmapDescriptor customIcon = createCustomMarkerIcon(route, isAccepted);
        if (customIcon == null) {
            Log.e(TAG, "Failed to create custom icon for bus: " + busId);
            return; // Skip update if icon creation fails
        }

        // Update existing marker or add a new one
        if (busMarkers.containsKey(busId)) {
            // Marker exists, update position and icon
            Marker marker = busMarkers.get(busId);
            if (marker != null) {
                marker.setPosition(busLatLng);
                marker.setIcon(customIcon);
                // Ensure the tag is still set (important for click listener)
                marker.setTag(busId);
            }
        } else {
            // Marker doesn't exist, create a new one
            Marker newMarker = mMap.addMarker(new MarkerOptions()
                    .position(busLatLng)
                    .icon(customIcon)
                    .anchor(0.5f, 1.0f)); // Anchor to bottom-center of the icon
            if (newMarker != null) {
                newMarker.setTag(busId); // Store busId in the marker's tag
                busMarkers.put(busId, newMarker); // Add to our tracking map
            } else {
                Log.e(TAG, "Failed to add marker to map for bus: " + busId);
            }
        }
    }

    // Helper function to force all markers to redraw (e.g., after acceptance status changes)
    private void redrawAllMarkers() {
        if (mMap == null || busesRef == null) return;
        Log.d(TAG, "Redrawing all markers. Accepted bus ID: " + acceptedBusId);
        // Iterate through the local HashMap of markers and update their icons
        for (String busId : busMarkers.keySet()) {
            Marker marker = busMarkers.get(busId);
            if (marker != null) {
                // We need the route text to recreate the icon
                // This requires fetching the route again or storing it locally
                // Let's modify updateBusMarker to handle redraw if data exists

                // A simpler way: Find the snapshot for this busId again?
                // No, that's inefficient. Let's just update the icon directly if possible.

                // Option: Re-fetch data just for this marker? Still inefficient.

                // Best approach: Store route locally or modify createCustomMarkerIcon
                // Let's modify createCustomMarkerIcon to use the marker's tag if needed? No, tag only has ID.

                // Simplest robust solution: Just call updateBusMarker again if we have the snapshot?
                // The ChildEventListener approach already handles this naturally.
                // We just need to ensure updateBusMarker correctly checks acceptedBusId.

                // Let's try directly updating the icon
                String currentRoute = ""; // How to get the route text reliably? Stored on marker title? No.

                // Let's rely on the ChildEventListener calling updateBusMarker.
                // The change in acceptedBusId will cause the correct color on the next update.
                // If we need immediate redraw, we need to fetch the data again.

                // Let's try getting the route from the marker title (if we set it)
                // Or better: Modify updateBusMarker slightly

                // Reworking: updateBusMarker already handles the isAccepted check.
                // We just need to ensure it runs again. How?
                // Re-attaching the listener is heavy-handed but works.
                // Let's iterate through the map and update icons directly.
                DatabaseReference specificBusRef = busesRef.child(busId);
                specificBusRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            updateBusMarker(dataSnapshot); // Re-run the update logic for this specific bus
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Failed to re-fetch bus data for redraw: " + busId, databaseError.toException());
                    }
                });


            }
        }
    }


    // Creates the custom marker icon (bitmap) from the layout
    private BitmapDescriptor createCustomMarkerIcon(String text, boolean isAccepted) {
        try {
            // Set the route text on the pre-inflated layout
            tvMarkerRoute.setText(text);

            // Apply or remove the color tint based on acceptance status
            if (isAccepted) {
                ivMarkerIcon.setColorFilter(Color.parseColor("#4CAF50"), PorterDuff.Mode.SRC_IN); // Green tint
            } else {
                ivMarkerIcon.clearColorFilter(); // No tint
            }

            // Measure and layout the custom view
            markerLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            markerLayout.layout(0, 0, markerLayout.getMeasuredWidth(), markerLayout.getMeasuredHeight());

            // Create a bitmap and draw the layout onto it
            Bitmap bitmap = Bitmap.createBitmap(markerLayout.getMeasuredWidth(), markerLayout.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            markerLayout.draw(canvas);

            // Convert the bitmap to a BitmapDescriptor for the map marker
            return BitmapDescriptorFactory.fromBitmap(bitmap);

        } catch (Exception e) {
            Log.e(TAG, "createCustomMarkerIcon: Error creating bitmap", e);
            return null; // Return null if bitmap creation fails
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up Firebase listeners to prevent memory leaks
        if (passengerBookingRef != null && bookingListener != null) {
            passengerBookingRef.removeEventListener(bookingListener);
        }
        // Consider removing the busesRef ChildEventListener here too if appropriate
    }
}