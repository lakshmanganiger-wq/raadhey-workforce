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
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationTrackingService extends Service {
    public static final String ACTION_START="com.raadhey.workforce.START_TRACKING";
    public static final String ACTION_STOP="com.raadhey.workforce.STOP_TRACKING";
    private static final String CHANNEL="raadhey_location";
    private LocationManager manager;
    private LocationListener listener;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    @Override public void onCreate(){ super.onCreate(); manager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){ NotificationChannel channel=new NotificationChannel(CHANNEL,"Work location tracking",NotificationManager.IMPORTANCE_LOW); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel); } }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_STOP.equals(intent.getAction())){ stopTracking(); stopSelf(); return START_NOT_STICKY; }
        Notification n;
        if(Build.VERSION.SDK_INT>=26) n=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("RAADHEY Workforce").setContentText("Work location sharing is active").setOngoing(true).build();
        else n=new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("RAADHEY Workforce").setContentText("Work location sharing is active").setOngoing(true).build();
        startForeground(7101,n); beginTracking(); return START_STICKY;
    }
    private void beginTracking(){
        if(listener!=null)return;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopSelf();return;}
        listener=new LocationListener(){ @Override public void onLocationChanged(Location l){
            SharedPreferencesHolder h=new SharedPreferencesHolder(getApplicationContext());
            if(!h.token.isEmpty()&&!h.uid.isEmpty()) io.execute(()->syncLocation(h,l));
        }};
        try{ String provider=manager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
            manager.requestLocationUpdates(provider,5*60*1000L,20f,listener,Looper.getMainLooper());
        }catch(SecurityException e){stopSelf();}
    }
    private void syncLocation(SharedPreferencesHolder h, Location l) {
        try {
            SupabaseClient.sendLocation(h.token,h.uid,l.getLatitude(),l.getLongitude(),l.getAccuracy());
        } catch(Exception first) {
            try {
                if(h.refresh.isEmpty()) return;
                org.json.JSONObject refreshed=SupabaseClient.refreshSession(h.refresh);
                String token=refreshed.optString("access_token");
                String refresh=refreshed.optString("refresh_token",h.refresh);
                if(token.isEmpty()) return;
                getSharedPreferences("raadhey_session",MODE_PRIVATE).edit()
                    .putString("access_token",token).putString("refresh_token",refresh).apply();
                SupabaseClient.sendLocation(token,h.uid,l.getLatitude(),l.getLongitude(),l.getAccuracy());
            } catch(Exception ignored) { }
        }
    }
    private void stopTracking(){if(listener!=null)manager.removeUpdates(listener);listener=null;stopForeground(true);}
    @Override public void onDestroy(){stopTracking();io.shutdown();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    private static class SharedPreferencesHolder{
        final String token,uid,refresh;
        SharedPreferencesHolder(Context c){android.content.SharedPreferences p=c.getSharedPreferences("raadhey_session",MODE_PRIVATE);token=p.getString("access_token","");uid=p.getString("user_id","");refresh=p.getString("refresh_token","");}
    }
}
