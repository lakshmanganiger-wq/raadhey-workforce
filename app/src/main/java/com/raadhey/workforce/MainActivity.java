package com.raadhey.workforce;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION=41, NOTIFICATION_PERMISSION=42;
    private static final int GREEN=0xFF075B46, IVORY=0xFFF7F5EF, GOLD=0xFF9A783D;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private SharedPreferences session;
    private Typeface lexend;
    private LinearLayout root;
    private EditText emailInput,passwordInput;
    private TextView message,status,locationInfo;
    private Button actionButton;
    private LocationManager locationManager;
    private boolean checkedIn=false;
    private String accessToken="",userId="",employeeName="Employee";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        session=getSharedPreferences("raadhey_session",MODE_PRIVATE);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        accessToken=session.getString("access_token",""); userId=session.getString("user_id","");
        employeeName=session.getString("full_name","Employee"); checkedIn=session.getBoolean("checked_in",false);
        try { lexend=Typeface.createFromAsset(getAssets(),"Lexend.ttf"); } catch(Exception e) { lexend=Typeface.create("sans-serif",0); }
        render();
    }

    private void render() {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24),dp(24),dp(24),dp(24)); root.setBackgroundColor(IVORY);
        ScrollView scroll=new ScrollView(this); scroll.addView(root);
        ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.raadhey_logo);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(74),dp(74)); lp.gravity=Gravity.CENTER; lp.bottomMargin=dp(8); root.addView(logo,lp);
        TextView brand=label("RAADHEY",29,GREEN,true); brand.setGravity(Gravity.CENTER); brand.setLetterSpacing(.08f); root.addView(brand,full());
        TextView company=label("TEXTILE PRINTING &\\nAPPAREL SOLUTIONS",11,GOLD,false); company.setGravity(Gravity.CENTER); company.setLetterSpacing(.07f); company.setPadding(0,dp(7),0,dp(20)); root.addView(company,full());
        TextView heading=label(accessToken.isEmpty()?"EMPLOYEE LOGIN":"WORKFORCE ATTENDANCE",13,GREEN,true); heading.setGravity(Gravity.CENTER); heading.setPadding(0,0,0,dp(22)); root.addView(heading,full());
        if(accessToken.isEmpty()) renderLogin(); else renderAttendance();
        setContentView(scroll);
    }

    private void renderLogin() {
        emailInput=new EditText(this); emailInput.setSingleLine(true); emailInput.setHint("Employee email"); emailInput.setTextSize(15); font(emailInput); root.addView(emailInput,full());
        passwordInput=new EditText(this); passwordInput.setSingleLine(true); passwordInput.setHint("Password"); passwordInput.setInputType(129); passwordInput.setTextSize(15); font(passwordInput); root.addView(passwordInput,full());
        Button login=button("SIGN IN"); LinearLayout.LayoutParams p=full(); p.topMargin=dp(24); root.addView(login,p);
        message=label("Sign in with the employee account created by your RAADHEY administrator.",12,0xFF666666,false); message.setPadding(0,dp(18),0,0); root.addView(message,full());
        login.setOnClickListener(v->signIn());
    }

    private void renderAttendance() {
        TextView welcome=label("Welcome, "+employeeName,16,GREEN,true); welcome.setPadding(0,dp(8),0,dp(18)); root.addView(welcome,full());
        status=label(checkedIn?"STATUS: CHECKED IN":"STATUS: NOT CHECKED IN",17,GREEN,true); status.setPadding(0,dp(24),0,dp(14)); root.addView(status,full());
        locationInfo=label("Factory boundary verification is required for attendance.",13,0xFF555555,false); locationInfo.setPadding(0,dp(8),0,dp(24)); root.addView(locationInfo,full());
        actionButton=button(checkedIn?"CHECK OUT":"CHECK IN"); root.addView(actionButton,full()); actionButton.setOnClickListener(v->getLocationAndMark());
        Button logout=button("SIGN OUT"); logout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF777777)); LinearLayout.LayoutParams q=full(); q.topMargin=dp(16); root.addView(logout,q);
        logout.setOnClickListener(v->{stopTracking(); session.edit().clear().apply(); accessToken=""; userId=""; checkedIn=false; render();});
        message=label("Attendance is synced to the RAADHEY cloud database.",11,0xFF777777,false); message.setGravity(Gravity.CENTER); message.setPadding(0,dp(22),0,0); root.addView(message,full());
    }

    private void signIn() {
        String email=emailInput.getText().toString().trim(), password=passwordInput.getText().toString();
        if(email.isEmpty()||password.isEmpty()){ toast("Enter your email and password."); return; }
        message.setText("Signing in…");
        io.execute(()->{
            try {
                JSONObject auth=SupabaseClient.signIn(email,password);
                String token=auth.optString("access_token"), uid=auth.optJSONObject("user")!=null?auth.getJSONObject("user").optString("id"):"";
                String refresh=auth.optString("refresh_token");
                if(token.isEmpty()||uid.isEmpty()) throw new Exception("The server did not return a valid employee session.");
                JSONObject profile=SupabaseClient.request("/rest/v1/profiles?select=full_name,role,active&id=eq."+uid,"GET",token,null);
                String raw=profile.optString("raw",""); String name="Employee";
                if(raw.startsWith("[")) {
                    org.json.JSONArray rows=new org.json.JSONArray(raw); JSONObject row=rows.optJSONObject(0);
                    if(row==null) throw new Exception("Employee profile not found. Ask the administrator to activate your account.");
                    if(!row.optBoolean("active",true)) throw new Exception("This employee account is inactive.");
                    name=row.optString("full_name","Employee");
                }
                final String display=name, refreshToken=refresh;
                runOnUiThread(()->{
                    accessToken=token; userId=uid; employeeName=display;
                    session.edit().putString("access_token",token).putString("refresh_token",refreshToken).putString("user_id",uid).putString("full_name",display).apply();
                    render();
                });
            } catch(Exception e){ runOnUiThread(()->message.setText(clean(e.getMessage()))); }
        });
    }

    private void getLocationAndMark() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
           checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION); return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION);
        try {
            String provider=locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
            locationInfo.setText("Checking your GPS position…");
            locationManager.requestSingleUpdate(provider,new LocationListener(){
                @Override public void onLocationChanged(Location loc){ submitAttendance(loc); }
            },Looper.getMainLooper());
        } catch(Exception e){ locationInfo.setText("Unable to get GPS. Enable Location and try again outdoors."); }
    }

    private void submitAttendance(Location loc) {
        if(loc.getAccuracy()>150){ locationInfo.setText("GPS accuracy is "+Math.round(loc.getAccuracy())+"m. Please retry outdoors."); return; }
        final boolean wasIn=checkedIn; actionButton.setEnabled(false); message.setText(wasIn?"Checking out…":"Checking in…");
        io.execute(()->{
            try {
                SupabaseClient.rpc(wasIn?"employee_check_out":"employee_check_in",accessToken,loc.getLatitude(),loc.getLongitude(),loc.getAccuracy());
                runOnUiThread(()->{
                    checkedIn=!wasIn; session.edit().putBoolean("checked_in",checkedIn).apply();
                    locationInfo.setText("GPS verified: "+String.format(Locale.US,"%.6f, %.6f",loc.getLatitude(),loc.getLongitude())+"  •  ±"+Math.round(loc.getAccuracy())+"m");
                    refreshAttendance(); actionButton.setEnabled(true); message.setText("Attendance synced successfully.");
                    if(checkedIn) startTracking(); else stopTracking();
                });
            } catch(Exception e){ runOnUiThread(()->{actionButton.setEnabled(true); message.setText(clean(e.getMessage())); locationInfo.setText(clean(e.getMessage()));}); }
        });
    }

    private void refreshAttendance(){ status.setText(checkedIn?"STATUS: CHECKED IN":"STATUS: NOT CHECKED IN"); actionButton.setText(checkedIn?"CHECK OUT":"CHECK IN"); }
    private void startTracking(){ Intent i=new Intent(this,LocationTrackingService.class); i.setAction(LocationTrackingService.ACTION_START); if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); }
    private void stopTracking(){ Intent i=new Intent(this,LocationTrackingService.class); i.setAction(LocationTrackingService.ACTION_STOP); startService(i); }
    private String clean(String s){ if(s==null)return "Something went wrong. Please try again."; try { JSONObject j=new JSONObject(s); return j.optString("msg",j.optString("message",s)); } catch(Exception e){ return s.length()>180?s.substring(0,180):s; } }
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
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}
