package com.raadhey.workforce;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationTrackingService extends Service {
    public static final String ACTION_START = "com.raadhey.workforce.START_TRACKING";
    public static final String ACTION_STOP = "com.raadhey.workforce.STOP_TRACKING";
    private static final String CHANNEL = "raadhey_location";
    private LocationManager manager;
    private LocationListener listener;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override public void onCreate() {
        super.onCreate();
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Work location tracking", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) { stopTracking(); stopSelf(); return START_NOT_STICKY; }
        startForeground(7101, buildNotification());
        beginTracking();
        return START_STICKY;
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("RAADHEY Workforce")
            .setContentText("Work location sharing is active")
            .setOngoing(true).build();
    }

    private void beginTracking() {
        if (listener != null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return; }
        listener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                String token = getSharedPreferences("raadhey_session", MODE_PRIVATE).getString("access_token", "");
                String userId = getSharedPreferences("raadhey_session", MODE_PRIVATE).getString("user_id", "");
                if (!token.isEmpty() && !userId.isEmpty()) io.execute(() -> {
                    try { SupabaseClient.sendLocation(token, userId, location.getLatitude(), location.getLongitude(), location.getAccuracy()); }
                    catch (Exception ignored) { }
                });
            }
        };
        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5 * 60 * 1000L, 20f, listener, Looper.getMainLooper());
            else manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5 * 60 * 1000L, 20f, listener, Looper.getMainLooper());
        } catch (SecurityException ignored) { stopSelf(); }
    }

    private void stopTracking() {
        if (listener != null) manager.removeUpdates(listener);
        listener = null;
        stopForeground(true);
    }
    @Override public void onDestroy() { stopTracking(); io.shutdown(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
