package com.raadhey.workforce;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.*;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import java.util.*;

public class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION=41, NOTIFICATION_PERMISSION=42;
    private static final int GREEN=0xFF075B46, IVORY=0xFFF7F5EF, GOLD=0xFF9A783D;
    private static final double FACTORY_LAT=13.0868273605, FACTORY_LNG=77.4191855295;
    private static final double GEOFENCE_RADIUS_M=100.0;
    private FirebaseAuth auth;
    private DatabaseReference db;
    private SharedPreferences session;
    private Typeface lexend;
    private LinearLayout root;
    private EditText emailInput,passwordInput;
    private TextView message,status,locationInfo;
    private Button actionButton;
    private LocationManager locationManager;
    private boolean checkedIn=false;
    private String userId="",employeeName="Employee",employeeCode="";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        auth=FirebaseAuth.getInstance();
        db=FirebaseDatabase.getInstance("https://raadhey-workforce-66e2a-default-rtdb.asia-southeast1.firebasedatabase.app").getReference();
        session=getSharedPreferences("raadhey_session",MODE_PRIVATE);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
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
        emailInput=new EditText(this); emailInput.setSingleLine(true); emailInput.setHint("Employee ID or email"); emailInput.setTextSize(15); font(emailInput); root.addView(emailInput,full());
        passwordInput=new EditText(this); passwordInput.setSingleLine(true); passwordInput.setHint("Password"); passwordInput.setInputType(129); passwordInput.setTextSize(15); font(passwordInput); root.addView(passwordInput,full());
        Button login=button("SIGN IN"); LinearLayout.LayoutParams p=full(); p.topMargin=dp(24); root.addView(login,p);
        message=label("Sign in with the employee account created by your RAADHEY administrator.",12,0xFF666666,false); message.setPadding(0,dp(18),0,0); root.addView(message,full());
        login.setOnClickListener(v->signIn());
    }

    private void signIn() {
        String entered=emailInput.getText().toString().trim();
        String email=entered.contains("@")?entered:entered.toLowerCase(Locale.US)+"@employees.raadhey.com";
        String password=passwordInput.getText().toString();
        if(entered.isEmpty()||password.isEmpty()){ toast("Enter your employee ID and password."); return; }
        message.setText("Signing in securely…");
        auth.signInWithEmailAndPassword(email,password).addOnCompleteListener(this, task->{
            if(!task.isSuccessful()){ message.setText(task.getException()!=null?task.getException().getLocalizedMessage():"Sign-in failed."); return; }
            FirebaseUser u=auth.getCurrentUser(); if(u==null){message.setText("Firebase session unavailable.");return;}
            userId=u.getUid(); loadProfile();
        });
    }

    private void loadProfile() {
        messageSafe("Loading employee profile…");
        db.child("profiles").child(userId).get().addOnCompleteListener(this, task->{
            if(!task.isSuccessful()||task.getResult()==null||!task.getResult().exists()){
                if(auth.getCurrentUser()!=null) auth.signOut();
                render(); if(message!=null) message.setText("Employee profile not found. Ask the RAADHEY administrator to create and activate your account."); return;
            }
            DataSnapshot p=task.getResult();
            if(!Boolean.TRUE.equals(p.child("active").getValue(Boolean.class)) || !"employee".equals(p.child("role").getValue(String.class))){
                auth.signOut(); render(); if(message!=null) message.setText("This account is inactive or is not an employee account."); return;
            }
            employeeName=Objects.toString(p.child("fullName").getValue(String.class),"Employee");
            employeeCode=Objects.toString(p.child("employeeCode").getValue(String.class),"");
            db.child("attendance").child(userId).child(dateKey()).get().addOnSuccessListener(a->{checkedIn=a.exists()&&a.child("checkOutAt").getValue()==null; render();});
        });
    }

    private void renderAttendance() {
        TextView welcome=label("Welcome, "+employeeName,16,GREEN,true); welcome.setPadding(0,dp(8),0,dp(18)); root.addView(welcome,full());
        status=label(checkedIn?"STATUS: CHECKED IN":"STATUS: NOT CHECKED IN",17,GREEN,true); status.setPadding(0,dp(24),0,dp(14)); root.addView(status,full());
        locationInfo=label("Factory GPS verification is required for attendance.",13,0xFF555555,false); locationInfo.setPadding(0,dp(8),0,dp(24)); root.addView(locationInfo,full());
        actionButton=button(checkedIn?"CHECK OUT":"CHECK IN"); root.addView(actionButton,full()); actionButton.setOnClickListener(v->getLocationAndMark());
        Button logout=button("SIGN OUT"); logout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF777777)); LinearLayout.LayoutParams q=full(); q.topMargin=dp(16); root.addView(logout,q);
        logout.setOnClickListener(v->{stopTracking(); auth.signOut(); session.edit().clear().apply(); checkedIn=false; render();});
        message=label("Attendance and GPS updates sync to RAADHEY Firebase.",11,0xFF777777,false); message.setGravity(Gravity.CENTER); message.setPadding(0,dp(22),0,0); root.addView(message,full());
    }

    private void getLocationAndMark() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION); return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION);
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
        float accuracy=loc.hasAccuracy()?loc.getAccuracy():9999;
        float[] distance=new float[1]; Location.distanceBetween(FACTORY_LAT,FACTORY_LNG,loc.getLatitude(),loc.getLongitude(),distance);
        if(accuracy>150){ locationInfo.setText("GPS accuracy is "+Math.round(accuracy)+"m. Please retry outdoors."); return; }
        if(distance[0]>GEOFENCE_RADIUS_M){ locationInfo.setText("You are outside the RAADHEY factory attendance boundary."); return; }
        actionButton.setEnabled(false); message.setText(checkedIn?"Checking out…":"Checking in…");
        String date=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
        String callable=checkedIn?"employeeCheckOut":"employeeCheckIn";
        Map<String,Object> payload=new HashMap<>();payload.put("latitude",loc.getLatitude());payload.put("longitude",loc.getLongitude());payload.put("accuracyM",accuracy);
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable(callable).call(payload).addOnCompleteListener(task->{
            actionButton.setEnabled(true);
            if(!task.isSuccessful()){Exception e=task.getException();message.setText(e!=null?e.getLocalizedMessage():"Attendance request failed.");return;}
            checkedIn=!checkedIn;refreshAttendance();
            locationInfo.setText("GPS verified: ±"+Math.round(accuracy)+"m; factory boundary validated.");
            message.setText("Attendance synced securely to Firebase.");
            if(checkedIn)startTracking();else stopTracking();
        });
    }

    private void refreshAttendance(){ status.setText(checkedIn?"STATUS: CHECKED IN":"STATUS: NOT CHECKED IN"); actionButton.setText(checkedIn?"CHECK OUT":"CHECK IN"); }
    private void startTracking(){ Intent i=new Intent(this,LocationTrackingService.class); i.setAction(LocationTrackingService.ACTION_START); if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); }
    private void stopTracking(){ Intent i=new Intent(this,LocationTrackingService.class); i.setAction(LocationTrackingService.ACTION_STOP); startService(i); }
    private String dateKey(){ java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US); f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Kolkata")); return f.format(new Date()); }
    private void messageSafe(String s){ if(message!=null)message.setText(s); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private TextView label(String s,int size,int color,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setTypeface(lexend,bold?Typeface.BOLD:Typeface.NORMAL); return t; }
    private void font(TextView t){t.setTypeface(lexend);}
    private Button button(String s){Button b=new Button(this); b.setText(s); b.setTextColor(0xFFFFFFFF); b.setTypeface(lexend,Typeface.BOLD); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN)); return b;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==LOCATION_PERMISSION){ boolean ok=false; for(int g:grants) if(g==PackageManager.PERMISSION_GRANTED) ok=true; if(ok)getLocationAndMark(); else toast("Location permission is required for attendance."); }
    }
}
