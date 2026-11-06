package com.raadhey.workforce;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

public class LocationTrackingService extends Service {
    public static final String ACTION_START="com.raadhey.workforce.START_TRACKING";
    public static final String ACTION_STOP="com.raadhey.workforce.STOP_TRACKING";
    private static final String CHANNEL="raadhey_location";
    private LocationManager manager;
    private LocationListener listener;
    @Override public void onCreate(){super.onCreate();manager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Work location tracking",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopTracking();stopSelf();return START_NOT_STICKY;}
        Notification n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("RAADHEY Workforce").setContentText("Work location sharing is active").setOngoing(true).build():new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("RAADHEY Workforce").setContentText("Work location sharing is active").setOngoing(true).build();
        startForeground(7101,n);beginTracking();return START_STICKY;
    }
    private void beginTracking(){
        if(listener!=null)return;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopSelf();return;}
        listener=new LocationListener(){@Override public void onLocationChanged(Location l){
            String uid=FirebaseAuth.getInstance().getUid();if(uid==null)return;
            DatabaseReference db=FirebaseDatabase.getInstance("https://raadhey-workforce-66e2a-default-rtdb.asia-southeast1.firebasedatabase.app").getReference();
            String date=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
            db.child("attendance").child(uid).child(date).get().addOnSuccessListener(a->{
                if(a.exists()&&a.child("checkOutAt").getValue()==null){
                    Map<String,Object> point=new HashMap<>();point.put("latitude",l.getLatitude());point.put("longitude",l.getLongitude());point.put("accuracyM",l.hasAccuracy()?l.getAccuracy():-1);point.put("recordedAt",ServerValue.TIMESTAMP);
                    db.child("liveLocations").child(uid).setValue(point);
                } else {stopTracking();stopSelf();}
            });
        }};
        try{String p=manager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;manager.requestLocationUpdates(p,5*60*1000L,20f,listener,Looper.getMainLooper());}
        catch(SecurityException e){stopSelf();}
    }
    private void stopTracking(){if(listener!=null)manager.removeUpdates(listener);listener=null;stopForeground(true);}
    @Override public void onDestroy(){stopTracking();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
