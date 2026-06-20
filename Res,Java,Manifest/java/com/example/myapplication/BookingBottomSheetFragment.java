package com.example.myapplication;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class BookingBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_BUS_ID = "busId";
    private String busId;

    private EditText etPassengerName, etPassengerLocation, etPassengerContact;
    private Button btnAssignBus;

    public static BookingBottomSheetFragment newInstance(String busId) {
        BookingBottomSheetFragment fragment = new BookingBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_BUS_ID, busId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            busId = getArguments().getString(ARG_BUS_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_booking_bottom_sheet, container, false);

        etPassengerName = view.findViewById(R.id.et_passenger_name);
        etPassengerLocation = view.findViewById(R.id.et_passenger_location);
        etPassengerContact = view.findViewById(R.id.et_passenger_contact);
        btnAssignBus = view.findViewById(R.id.btn_assign_bus);

        btnAssignBus.setOnClickListener(v -> sendBookingRequest());

        return view;
    }

    private void sendBookingRequest() {
        String name = etPassengerName.getText().toString().trim();
        String location = etPassengerLocation.getText().toString().trim();
        String contact = etPassengerContact.getText().toString().trim();

        // --- NEW: Get the current passenger's User ID ---
        FirebaseUser passengerUser = FirebaseAuth.getInstance().getCurrentUser();
        if (passengerUser == null) {
            Toast.makeText(getContext(), "Error: You are not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }
        String passengerUid = passengerUser.getUid();
        // ---------------------------------------------

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(location) || TextUtils.isEmpty(contact)) {
            Toast.makeText(getContext(), "Please fill in all details", Toast.LENGTH_SHORT).show();
            return;
        }

        if (busId == null) {
            Toast.makeText(getContext(), "Error: No Bus ID selected", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference bookingRef = FirebaseDatabase.getInstance().getReference("buses")
                .child(busId)
                .child("booking_requests")
                .push();

        HashMap<String, String> bookingData = new HashMap<>();
        bookingData.put("passengerName", name);
        bookingData.put("passengerLocation", location);
        bookingData.put("passengerContact", contact);
        bookingData.put("passengerUid", passengerUid); // --- NEW: Add the UID to the request ---

        bookingRef.setValue(bookingData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Booking request sent!", Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to send request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}