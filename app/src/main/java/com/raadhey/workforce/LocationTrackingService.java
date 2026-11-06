package com.raadhey.workforce;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class LocationTrackingService extends Service {
    public static final String ACTION_START="com.raadhey.workforce.START_TRACKING";
    public static final String ACTION_STOP="com.raadhey.workforce.STOP_TRACKING";
    private static final String CHANNEL="raadhey_location";
    private LocationManager manager;
    private LocationListener listener;
    private DatabaseReference db;
    private String dateKey;
    @Override public void onCreate(){
        super.onCreate(); manager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        db=FirebaseDatabase.getInstance("https://raadhey-workforce-66e2a-default-rtdb.asia-southeast1.firebasedatabase.app").getReference();
        dateKey=localDateKey();
        if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Work location tracking",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopTracking();stopSelf();return START_NOT_STICKY;}
        Notification n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("RAADHEY Workforce").setContentText("Work location sharing is active").setOngoing(true).build():new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("RAADHEY Workforce").setContentText("Work location sharing is active").setOngoing(true).build();
        startForeground(7101,n); beginTracking(); return START_STICKY;
    }
    private void beginTracking(){
        if(listener!=null)return;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopSelf();return;}
        String uid=FirebaseAuth.getInstance().getUid(); if(uid==null){stopSelf();return;}
        dateKey=localDateKey();
        db.child("attendance").child(uid).child(dateKey).get().addOnSuccessListener(snapshot->{
            if(!snapshot.exists()||snapshot.child("checkOutAt").exists()){stopSelf();return;}
            listener=new LocationListener(){
                @Override public void onLocationChanged(Location l){
                    String currentUid=FirebaseAuth.getInstance().getUid(); if(currentUid==null)return;
                    Map<String,Object> point=new HashMap<>();
                    point.put("latitude",l.getLatitude()); point.put("longitude",l.getLongitude());
                    point.put("accuracyM",l.hasAccuracy()?l.getAccuracy():-1);
                    point.put("updatedAt",ServerValue.TIMESTAMP); point.put("attendanceDate",dateKey);
                    db.child("liveLocations").child(currentUid).setValue(point);
                }
                @Override public void onProviderDisabled(String p){}
                @Override public void onProviderEnabled(String p){}
                @Override public void onStatusChanged(String p,int s,Bundle b){}
            };
            try{
                String provider=manager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
                manager.requestLocationUpdates(provider,5*60*1000L,20f,listener,Looper.getMainLooper());
                Location last=manager.getLastKnownLocation(provider); if(last!=null) listener.onLocationChanged(last);
            }catch(SecurityException e){stopSelf();}
        }).addOnFailureListener(e->stopSelf());
    }
    private void stopTracking(){if(listener!=null)manager.removeUpdates(listener);listener=null;stopForeground(true);}
    private String localDateKey(){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));return f.format(new Date());}
    @Override public void onDestroy(){stopTracking();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
