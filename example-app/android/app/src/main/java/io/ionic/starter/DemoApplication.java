package io.ionic.starter;

import android.app.Application;
import android.util.Log;

import ee.forgr.capacitor.streamcall.StreamCallPlugin;

public class DemoApplication extends Application {
    private static final String TAG = "DemoApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application onCreate called");
        initializeApp();
    }

    private void initializeApp() {
        Log.i(TAG, "Initializing application...");
        // Initialize Firebase
         try {
            StreamCallPlugin pl = (new StreamCallPlugin());
            pl.initializeStreamVideo(this);
             Log.i(TAG, "Firebase initialized successfully");
         } catch (Exception e) {
             Log.e(TAG, "Failed to initialize Firebase: " + e.getMessage());
         }
        Log.i(TAG, "Application initialization completed");
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "System is running low on memory");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "onTrimMemory called with level: " + level);
    }
}
