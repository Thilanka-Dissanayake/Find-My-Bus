package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * This Adapter manages the list of PassengerInfo objects
 * and binds them to the passenger_info_item.xml layout.
 */
public class PassengerAdapter extends RecyclerView.Adapter<PassengerAdapter.PassengerViewHolder> {

    private List<PassengerInfo> passengerList;

    // --- NEW: Interface to send click events back to the Activity ---
    public interface OnPassengerPickupListener {
        void onPassengerPickedUp(PassengerInfo passenger, int position);
    }
    private OnPassengerPickupListener pickupListener;

    public void setOnPassengerPickupListener(OnPassengerPickupListener listener) {
        this.pickupListener = listener;
    }
    // ----------------------------------------------------------------

    public PassengerAdapter(List<PassengerInfo> passengerList) {
        this.passengerList = passengerList;
    }

    @NonNull
    @Override
    public PassengerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.passenger_info_item, parent, false);
        return new PassengerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PassengerViewHolder holder, int position) {
        PassengerInfo passenger = passengerList.get(position);

        holder.tvName.setText(passenger.getName());
        holder.tvLocation.setText("Pickup: " + passenger.getLocation());
        holder.tvContact.setText("Contact: " + passenger.getContact());

        // --- NEW: Set the checkbox state ---
        holder.cbPickedUp.setOnCheckedChangeListener(null); // Clear old listener to prevent bugs

        if (passenger.isPickedUp()) {
            holder.cbPickedUp.setChecked(true);
            holder.cbPickedUp.setEnabled(false); // Already picked up, disable it
        } else {
            holder.cbPickedUp.setChecked(false);
            holder.cbPickedUp.setEnabled(true); // Ready to be picked up
        }

        // Set the listener for the checkbox
        holder.cbPickedUp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && pickupListener != null) {
                // Only fire the event if we are checking it ON
                pickupListener.onPassengerPickedUp(passenger, position);
            }
        });
        // ------------------------------------
    }

    @Override
    public int getItemCount() {
        return passengerList.size();
    }

    // --- NEW: Updated ViewHolder to include CheckBox ---
    public static class PassengerViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvLocation, tvContact;
        CheckBox cbPickedUp;

        public PassengerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_passenger_name_item);
            tvLocation = itemView.findViewById(R.id.tv_passenger_location_item);
            tvContact = itemView.findViewById(R.id.tv_passenger_contact_item);
            cbPickedUp = itemView.findViewById(R.id.cb_picked_up);
        }
    }
}