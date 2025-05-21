package io.ionic.starter;

import android.os.Bundle;
import android.content.Intent;
import android.util.Log;

import com.getcapacitor.PluginHandle;
import com.google.firebase.FirebaseApp;

import com.getcapacitor.BridgeActivity;

import ee.forgr.capacitor.streamcall.StreamCallPlugin;

public class MainActivity extends BridgeActivity {

  private static final String TAG = "MainActivity";

//  MainActivity() {
//
//  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // Save initial intent for StreamCallPlugin (handles killed-state notification)
    ee.forgr.capacitor.streamcall.StreamCallPlugin.saveInitialIntent(getIntent());

    super.onCreate(savedInstanceState);
//    StreamCallPlugin pl = (new StreamCallPlugin());
//    pl.setBridge(this.getBridge());
//    pl.initializeStreamVideo(true);
//
//    Log.d(TAG, "onCreate called");
//    if (savedInstanceState != null) {
//      Log.d(TAG, "savedInstanceState contents:");
//      logBundle(savedInstanceState);
//    } else {
//      Log.d(TAG, "savedInstanceState is null");
//    }

//    FirebaseApp.initializeApp(this);

    // Log the initial intent
    Intent intent = getIntent();
    logIntent(intent);

    // Ensure the activity is visible over the lock screen when launched via full-screen intent
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true);
      setTurnScreenOn(true);
    } else {
      getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    Log.d(TAG, "onRestoreInstanceState called, restoring bundle:");
    logBundle(savedInstanceState);
  }

  private void logBundle(Bundle bundle) {
    if (bundle == null) {
      Log.d(TAG, "  Bundle is null");
      return;
    }

    for (String key : bundle.keySet()) {
      Object value = bundle.get(key);
      String valueType = value != null ? value.getClass().getSimpleName() : "null";
      String valueStr = value != null ? value.toString() : "null";
      Log.d(TAG, String.format("  %s (%s): %s", key, valueType, valueStr));

      // If this is a nested bundle, log its contents too
      if (value instanceof Bundle) {
        Log.d(TAG, "  Nested bundle for key: " + key);
        logBundle((Bundle) value);
      }
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
      super.onNewIntent(intent);
      logIntent(intent);
      setIntent(intent);
      // Check for accept call action
      String action = intent.getAction();
      Log.d("MainActivity", "onNewIntent: Received intent with action: " + action);
      if ("io.getstream.video.android.action.ACCEPT_CALL".equals(action)) {
          Log.d("MainActivity", "onNewIntent: ACCEPT_CALL action received");

          android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
          if (km != null && km.isKeyguardLocked()) {
              Log.d("MainActivity", "Device is locked – requesting dismiss before handling call");
              km.requestDismissKeyguard(this, new android.app.KeyguardManager.KeyguardDismissCallback() {
                  @Override
                  public void onDismissSucceeded() {
                      Log.d("MainActivity", "Keyguard dismissed, forwarding intent to StreamCallPlugin");
                      forwardAcceptIntent(intent);
                  }

                  @Override
                  public void onDismissCancelled() {
                      Log.d("MainActivity", "Keyguard dismiss cancelled");
                  }
              });
          } else {
              forwardAcceptIntent(intent);
          }
      }
  }

  private void forwardAcceptIntent(Intent intent) {
      ee.forgr.capacitor.streamcall.StreamCallPlugin.saveInitialIntent(intent);
      PluginHandle pluginHandle = getBridge().getPlugin("StreamCall");
      if (pluginHandle != null) {
          com.getcapacitor.Plugin pluginInstance = pluginHandle.getInstance();
          if (pluginInstance instanceof ee.forgr.capacitor.streamcall.StreamCallPlugin) {
              ((ee.forgr.capacitor.streamcall.StreamCallPlugin) pluginInstance).handleAcceptCallIntent(intent);
          }
      }
  }

  private void logIntent(Intent intent) {
    if (intent == null) {
      Log.d(TAG, "Received null intent");
      return;
    }

    Log.d(TAG, "Received intent:");
    Log.d(TAG, "  Action: " + intent.getAction());
    Log.d(TAG, "  Data URI: " + intent.getDataString());
    Log.d(TAG, "  Type: " + intent.getType());
    Log.d(TAG, "  Package: " + intent.getPackage());
    Log.d(TAG, "  Component: " + (intent.getComponent() != null ? intent.getComponent().flattenToString() : "null"));

    Bundle extras = intent.getExtras();
    if (extras != null) {
      Log.d(TAG, "  Extras:");
      for (String key : extras.keySet()) {
        Object value = extras.get(key);
        Log.d(TAG, "    " + key + ": " + (value != null ? value.toString() : "null"));
      }
    } else {
      Log.d(TAG, "  No extras");
    }

    String[] categories = intent.getCategories() != null ?
        intent.getCategories().toArray(new String[0]) : new String[0];
    Log.d(TAG, "  Categories: " + String.join(", ", categories));

    int flags = intent.getFlags();
    Log.d(TAG, "  Flags: " + String.format("0x%08X", flags));
  }

  @Override
  public void onResume() {
    super.onResume();
  }
}
