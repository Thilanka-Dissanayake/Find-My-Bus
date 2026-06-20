package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private EditText editTextEmail, editTextPassword, editTextRoute;
    private Button buttonLogin;
    private TextView textViewRegister;
    private FirebaseAuth mAuth;
    private DatabaseReference userDatabase;

    // Key used to pass the route to DriverActivity
    public static final String EXTRA_ROUTE = "com.example.myapplication.ROUTE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        userDatabase = FirebaseDatabase.getInstance().getReference("users");

        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextRoute = findViewById(R.id.editTextRoute);
        buttonLogin = findViewById(R.id.buttonLogin);
        textViewRegister = findViewById(R.id.textViewRegister);

        buttonLogin.setOnClickListener(v -> loginUser());

        textViewRegister.setOnClickListener(v -> {
            // Go to the registration screen
            startActivity(new Intent(MainActivity.this, RegisterActivity.class));
        });
    }

    private void loginUser() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        // Get the route entered by the driver
        final String route = editTextRoute.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            editTextEmail.setError("Email is required.");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            editTextPassword.setError("Password is required.");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Login successful, check the user's role
                            checkUserRoleAndRedirect(route);
                        } else {
                            // Login failed
                            Toast.makeText(MainActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkUserRoleAndRedirect(String route) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null) {
            String userId = firebaseUser.getUid();

            // Read the user's profile data from Firebase
            userDatabase.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String role = dataSnapshot.child("role").getValue(String.class);

                    if (role != null) {
                        Toast.makeText(MainActivity.this, "Login Successful.", Toast.LENGTH_SHORT).show();

                        if (role.equals("Bus Driver")) {
                            // User is a driver, read their assigned busId
                            String busId = dataSnapshot.child("busId").getValue(String.class);

                            if (busId == null || busId.isEmpty()) {
                                // Safety check in case busId is missing
                                Toast.makeText(MainActivity.this, "Driver account error: busId not found.", Toast.LENGTH_LONG).show();
                                return;
                            }

                            // Start DriverActivity and pass both route and busId
                            Intent intent = new Intent(MainActivity.this, DriverActivity.class);
                            intent.putExtra(EXTRA_ROUTE, route);
                            intent.putExtra(DriverActivity.EXTRA_BUS_ID, busId); // Pass the unique busId
                            startActivity(intent);

                        } else {
                            // User is a passenger, start PassengerActivity
                            startActivity(new Intent(MainActivity.this, PassengerActivity.class));
                        }
                        finish(); // Close the login activity
                    } else {
                        // Role not found in the database
                        Toast.makeText(MainActivity.this, "User role not found.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    // Failed to read data from Firebase
                    Toast.makeText(MainActivity.this, "Failed to read user role.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}