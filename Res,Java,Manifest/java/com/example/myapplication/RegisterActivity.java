package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log; // Make sure this is imported
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class RegisterActivity extends AppCompatActivity {

    private EditText editTextEmail, editTextPassword;
    private RadioGroup radioGroupRole;
    private Button buttonRegister;
    private TextView textViewLogin;
    private FirebaseAuth mAuth;
    private DatabaseReference userDatabase;
    private static final String TAG = "RegisterActivity"; // Tag for Logcat

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        userDatabase = FirebaseDatabase.getInstance().getReference("users");

        editTextEmail = findViewById(R.id.editTextEmailRegister);
        editTextPassword = findViewById(R.id.editTextPasswordRegister);
        radioGroupRole = findViewById(R.id.radioGroupRole);
        buttonRegister = findViewById(R.id.buttonRegister);
        textViewLogin = findViewById(R.id.textViewLogin);

        buttonRegister.setOnClickListener(v -> registerUser());

        textViewLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
        });
    }

    private void registerUser() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        Log.d(TAG, "Register button clicked."); // Log start

        if (TextUtils.isEmpty(email)) {
            editTextEmail.setError("Email is required.");
            Log.w(TAG, "Registration failed: Email empty."); // Log validation fail
            return;
        }

        if (TextUtils.isEmpty(password)) {
            editTextPassword.setError("Password is required (min 6 characters).");
            Log.w(TAG, "Registration failed: Password empty."); // Log validation fail
            return;
        }

        if (password.length() < 6) {
            editTextPassword.setError("Password must be at least 6 characters.");
            Log.w(TAG, "Registration failed: Password too short."); // Log validation fail
            return;
        }

        int selectedRoleId = radioGroupRole.getCheckedRadioButtonId();
        final String role = (selectedRoleId == R.id.radioButtonBusDriver) ? "Bus Driver" : "Passenger";

        Log.d(TAG, "Attempting to register user with email: " + email + " and role: " + role);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser firebaseUser = mAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                String userId = firebaseUser.getUid();
                                Log.d(TAG, "User created successfully. UID: " + userId);
                                Log.d(TAG, "Saving user data...");

                                // Save the role
                                userDatabase.child(userId).child("role").setValue(role)
                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Role saved successfully."))
                                        .addOnFailureListener(e -> Log.e(TAG, "Failed to save role.", e));

                                // If it's a driver, also save a unique busId
                                if (role.equals("Bus Driver")) {
                                    Log.d(TAG, "User is a driver, attempting to save busId: " + userId);
                                    userDatabase.child(userId).child("busId").setValue(userId)
                                            .addOnSuccessListener(aVoid -> Log.d(TAG, "busId saved successfully."))
                                            .addOnFailureListener(e -> Log.e(TAG, "Failed to save busId.", e));
                                }

                                Toast.makeText(RegisterActivity.this, "Registration successful.", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                finish();
                            } else {
                                Log.w(TAG, "createUserWithEmail:success but firebaseUser is null");
                                Toast.makeText(RegisterActivity.this, "Registration failed: Could not get user info.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // Registration failed
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            // Display a more detailed error message
                            String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Toast.makeText(RegisterActivity.this, "Registration failed: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}

