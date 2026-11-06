package com.raadhey.workforce;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.*;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION=41, NOTIFICATION_PERMISSION=42;
    private static final int GREEN=0xFF075B46, IVORY=0xFFF7F5EF, GOLD=0xFF9A783D;
    // Confirmed RAADHEY factory boundary, in clockwise order: NW, NE, SE, SW.
    private static final double[][] FACTORY_BOUNDARY = {
        {13.0869225, 77.4191348}, // North-West
        {13.0868676, 77.4192857}, // North-East
        {13.0867285, 77.4192381}, // South-East
        {13.0867945, 77.4190815}  // South-West
    };
    private FirebaseAuth auth;
    private DatabaseReference db;
    private Typeface lexend;
    private LinearLayout root;
    private EditText employeeInput,passwordInput;
    private TextView message,status,locationInfo;
    private Button actionButton;
    private LocationManager locationManager;
    // 0 = not checked in, 1 = checked in, 2 = checked out (day completed)
    private int attendanceState=0;
    private String userId="",employeeName="Employee",employeeCode="";
    private String todayKey="";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        auth=FirebaseAuth.getInstance();
        db=FirebaseDatabase.getInstance("https://raadhey-workforce-66e2a-default-rtdb.asia-southeast1.firebasedatabase.app").getReference();
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        todayKey=dateKey();
        try { lexend=Typeface.createFromAsset(getAssets(),"Lexend.ttf"); } catch(Exception e) { lexend=Typeface.create("sans-serif",0); }
        FirebaseUser user=auth.getCurrentUser();
        if(user!=null){ userId=user.getUid(); loadProfile(); } else render();
    }

    private void render() {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24),dp(24),dp(24),dp(24)); root.setBackgroundColor(IVORY);
        ScrollView scroll=new ScrollView(this); scroll.addView(root);
        ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.raadhey_logo);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(74),dp(74)); lp.gravity=Gravity.CENTER; lp.bottomMargin=dp(8); root.addView(logo,lp);
        TextView brand=label("RAADHEY",29,GREEN,true); brand.setGravity(Gravity.CENTER); brand.setLetterSpacing(.08f); root.addView(brand,full());
        TextView company=label("TEXTILE PRINTING &\nAPPAREL SOLUTIONS",11,GOLD,false); company.setGravity(Gravity.CENTER); company.setLetterSpacing(.07f); company.setPadding(0,dp(7),0,dp(20)); root.addView(company,full());
        TextView heading=label(auth.getCurrentUser()==null?"EMPLOYEE LOGIN":"WORKFORCE ATTENDANCE",13,GREEN,true); heading.setGravity(Gravity.CENTER); heading.setPadding(0,0,0,dp(22)); root.addView(heading,full());
        if(auth.getCurrentUser()==null) renderLogin(); else renderAttendance();
        setContentView(scroll);
    }

    private void renderLogin() {
        employeeInput=new EditText(this); employeeInput.setSingleLine(true); employeeInput.setHint("Employee ID"); employeeInput.setTextSize(15); font(employeeInput); root.addView(employeeInput,full());
        passwordInput=new EditText(this); passwordInput.setSingleLine(true); passwordInput.setHint("Password"); passwordInput.setInputType(129); passwordInput.setTextSize(15); font(passwordInput); root.addView(passwordInput,full());
        Button login=button("SIGN IN"); LinearLayout.LayoutParams p=full(); p.topMargin=dp(24); root.addView(login,p);
        message=label("Sign in with your RAADHEY employee ID and password.",12,0xFF666666,false); message.setPadding(0,dp(18),0,0); root.addView(message,full());
        login.setOnClickListener(v->signIn());
    }

    private void signIn() {
        String entered=employeeInput.getText().toString().trim();
        String email=entered.contains("@")?entered:entered.toLowerCase(Locale.US)+"@employees.raadhey.com";
        String password=passwordInput.getText().toString();
        if(entered.isEmpty()||password.isEmpty()){ toast("Enter your employee ID and password."); return; }
        message.setText("Signing in…");
        auth.signInWithEmailAndPassword(email,password).addOnCompleteListener(this, task->{
            if(!task.isSuccessful()){ message.setText(task.getException()!=null?task.getException().getLocalizedMessage():"Sign-in failed."); return; }
            FirebaseUser u=auth.getCurrentUser(); if(u==null){message.setText("Firebase session unavailable.");return;}
            userId=u.getUid(); loadProfile();
        });
    }

    private void loadProfile() {
        if(message!=null) message.setText("Loading employee profile…");
        db.child("profiles").child(userId).get().addOnCompleteListener(this, task->{
            if(!task.isSuccessful()||task.getResult()==null||!task.getResult().exists()){
                auth.signOut(); render(); if(message!=null) message.setText("Employee profile not found. Ask the administrator to create your account in Firebase."); return;
            }
            DataSnapshot p=task.getResult();
            if(!Boolean.TRUE.equals(p.child("active").getValue(Boolean.class)) || !"employee".equals(p.child("role").getValue(String.class))){
                auth.signOut(); render(); if(message!=null) message.setText("This account is inactive or is not an employee account."); return;
            }
            employeeName=Objects.toString(p.child("fullName").getValue(String.class),"Employee");
            employeeCode=Objects.toString(p.child("employeeCode").getValue(String.class),"");
            todayKey=dateKey();
            db.child("attendance").child(userId).child(todayKey).get().addOnSuccessListener(a->{
                if(!a.exists()) attendanceState=0;
                else if(a.child("checkOutAt").exists()) attendanceState=2;
                else attendanceState=1;
                render();
                if(attendanceState==1) startTracking();
            });
        });
    }

    private void renderAttendance() {
        TextView welcome=label("Welcome, "+employeeName,16,GREEN,true); welcome.setPadding(0,dp(8),0,dp(18)); root.addView(welcome,full());
        String stateText=attendanceState==0?"NOT CHECKED IN":attendanceState==1?"ON DUTY — CHECKED IN":"COMPLETED FOR TODAY";
        status=label("STATUS: "+stateText,17,GREEN,true); status.setPadding(0,dp(24),0,dp(14)); root.addView(status,full());
        locationInfo=label(attendanceState==1?"Live location sharing is active during duty hours.":"Factory GPS verification is required for attendance.",13,0xFF555555,false);
        locationInfo.setPadding(0,dp(8),0,dp(24)); root.addView(locationInfo,full());
        if(attendanceState!=2) {
            actionButton=button(attendanceState==1?"CHECK OUT":"CHECK IN"); root.addView(actionButton,full());
            actionButton.setOnClickListener(v->getLocationAndMark());
        } else {
            TextView done=label("Your attendance is locked for today. You cannot check in or out again.",13,GOLD,true);
            done.setPadding(0,dp(18),0,dp(18)); root.addView(done,full());
        }
        if(attendanceState!=1) {
            Button logout=button("SIGN OUT"); logout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF777777));
            LinearLayout.LayoutParams q=full(); q.topMargin=dp(16); root.addView(logout,q);
            logout.setOnClickListener(v->{ auth.signOut(); attendanceState=0; render(); });
        }
        message=label(attendanceState==1?"Location is shared with RAADHEY while you are on duty.": "One check-in and one check-out are allowed per day.",11,0xFF777777,false);
        message.setGravity(Gravity.CENTER); message.setPadding(0,dp(22),0,0); root.addView(message,full());
    }

    private void getLocationAndMark() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION); return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION);
        }
        try {
            String provider=locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
            locationInfo.setText("Checking your GPS position…");
            locationManager.requestSingleUpdate(provider,new LocationListener(){
                @Override public void onLocationChanged(Location loc){ submitAttendance(loc); }
                @Override public void onProviderDisabled(String p){}
                @Override public void onProviderEnabled(String p){}
                @Override public void onStatusChanged(String p,int s,Bundle b){}
            },Looper.getMainLooper());
        } catch(Exception e){ locationInfo.setText("Unable to get GPS. Enable Location and try again outdoors."); }
    }

    private void submitAttendance(Location loc) {
        if(attendanceState==2){ toast("Attendance is already completed for today."); return; }
        float accuracy=loc.hasAccuracy()?loc.getAccuracy():9999;
        if(accuracy>150){ locationInfo.setText("GPS accuracy is "+Math.round(accuracy)+"m. Please retry outdoors."); return; }
        if(!isInsideFactoryBoundary(loc.getLatitude(),loc.getLongitude())){ locationInfo.setText("You are outside the RAADHEY factory attendance boundary."); return; }
        actionButton.setEnabled(false); message.setText(attendanceState==1?"Checking out…":"Checking in…");
        DatabaseReference day=db.child("attendance").child(userId).child(todayKey);
        if(attendanceState==0) {
            Map<String,Object> record=new HashMap<>();
            record.put("employeeCode",employeeCode); record.put("employeeName",employeeName);
            record.put("checkInAt",ServerValue.TIMESTAMP); record.put("checkInLatitude",loc.getLatitude());
            record.put("checkInLongitude",loc.getLongitude()); record.put("checkInAccuracyM",accuracy);
            Calendar cal=Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")); boolean late=cal.get(Calendar.HOUR_OF_DAY)>9 || (cal.get(Calendar.HOUR_OF_DAY)==9 && cal.get(Calendar.MINUTE)>15);
            record.put("late",late);
            day.setValue(record).addOnCompleteListener(task->{
                actionButton.setEnabled(true);
                if(!task.isSuccessful()){ message.setText("Check-in failed: "+(task.getException()!=null?task.getException().getLocalizedMessage():"Database error")); return; }
                attendanceState=1; render(); startTracking();
            });
        } else {
            Map<String,Object> updates=new HashMap<>();
            updates.put("checkOutAt",ServerValue.TIMESTAMP); updates.put("checkOutLatitude",loc.getLatitude());
            updates.put("checkOutLongitude",loc.getLongitude()); updates.put("checkOutAccuracyM",accuracy);
            Map<String,Object> rootUpdates=new HashMap<>();
            rootUpdates.put("attendance/"+userId+"/"+todayKey+"/checkOutAt",ServerValue.TIMESTAMP);
            rootUpdates.put("attendance/"+userId+"/"+todayKey+"/checkOutLatitude",loc.getLatitude());
            rootUpdates.put("attendance/"+userId+"/"+todayKey+"/checkOutLongitude",loc.getLongitude());
            rootUpdates.put("attendance/"+userId+"/"+todayKey+"/checkOutAccuracyM",accuracy);
            rootUpdates.put("liveLocations/"+userId,null);
            db.updateChildren(rootUpdates).addOnCompleteListener(task->{
                actionButton.setEnabled(true);
                if(!task.isSuccessful()){ message.setText("Check-out failed: "+(task.getException()!=null?task.getException().getLocalizedMessage():"Database error")); return; }
                stopTracking(); attendanceState=2; render();
            });
        }
    }

    // Ray-casting point-in-polygon check against the four confirmed factory corners.
    private boolean isInsideFactoryBoundary(double latitude,double longitude){
        boolean inside=false;
        int j=FACTORY_BOUNDARY.length-1;
        for(int i=0;i<FACTORY_BOUNDARY.length;i++){
            double yi=FACTORY_BOUNDARY[i][0], xi=FACTORY_BOUNDARY[i][1];
            double yj=FACTORY_BOUNDARY[j][0], xj=FACTORY_BOUNDARY[j][1];
            boolean crosses=((yi>latitude)!=(yj>latitude)) &&
                (longitude < (xj-xi)*(latitude-yi)/(yj-yi)+xi);
            if(crosses) inside=!inside;
            j=i;
        }
        return inside;
    }

    private void startTracking(){
        Intent i=new Intent(this,LocationTrackingService.class); i.setAction(LocationTrackingService.ACTION_START);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
    }
    private void stopTracking(){ Intent i=new Intent(this,LocationTrackingService.class); i.setAction(LocationTrackingService.ACTION_STOP); startService(i); }
    private String dateKey(){ SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US); f.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata")); return f.format(new Date()); }
    private TextView label(String s,int size,int color,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setTypeface(lexend,bold?Typeface.BOLD:Typeface.NORMAL); return t; }
    private void font(TextView t){t.setTypeface(lexend);}
    private Button button(String s){Button b=new Button(this); b.setText(s); b.setTextColor(0xFFFFFFFF); b.setTypeface(lexend,Typeface.BOLD); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN)); return b;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==LOCATION_PERMISSION){ boolean ok=false; for(int g:grants) if(g==PackageManager.PERMISSION_GRANTED) ok=true; if(ok)getLocationAndMark(); else toast("Location permission is required for attendance."); }
    }
}
