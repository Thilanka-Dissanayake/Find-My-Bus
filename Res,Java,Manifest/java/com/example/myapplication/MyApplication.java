package com.example.myapplication;

import android.app.Application;
import com.google.firebase.FirebaseApp;

/**
 * Custom Application class to handle one-time initializations.
 */
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Firebase right when the application starts.
        // This ensures that Firebase is ready before any activity tries to use it.
        FirebaseApp.initializeApp(this);
    }
}
