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
import com.google.firebase.FirebaseApp;
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
    private CheckBox keepSignedIn;
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
        SharedPreferences sessionPrefs=getSharedPreferences("raadhey_session",MODE_PRIVATE);
        FirebaseUser user=auth.getCurrentUser();
        if(user!=null && !sessionPrefs.getBoolean("keep_signed_in",true)){
            auth.signOut();
            user=null;
        }
        if(user!=null){ userId=user.getUid(); loadProfile(); } else render();
    }

    private void render() {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24),dp(24),dp(24),dp(24)); root.setBackgroundColor(IVORY);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root,new ScrollView.LayoutParams(-1,-1));
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
        keepSignedIn=new CheckBox(this); keepSignedIn.setText("Keep me signed in on this device"); keepSignedIn.setTextColor(0xFF555555); keepSignedIn.setTypeface(lexend); keepSignedIn.setTextSize(12);
        keepSignedIn.setChecked(getSharedPreferences("raadhey_session",MODE_PRIVATE).getBoolean("keep_signed_in",true));
        LinearLayout.LayoutParams keepParams=full(); keepParams.topMargin=dp(8); root.addView(keepSignedIn,keepParams);
        Button login=button("SIGN IN"); LinearLayout.LayoutParams p=full(); p.topMargin=dp(24); root.addView(login,p);
        message=label("Sign in with your RAADHEY employee ID and password.",12,0xFF666666,false); message.setPadding(0,dp(18),0,0); root.addView(message,full());
        login.setOnClickListener(v->signIn());
    }

    private void signIn() {
        String entered=employeeInput.getText().toString().trim();
        String email=entered.contains("@")?entered:entered.toLowerCase(Locale.US)+"@employees.raadhey.com";
        String password=passwordInput.getText().toString();
        if(entered.isEmpty()||password.isEmpty()){ toast("Enter your employee ID and password."); return; }
        getSharedPreferences("raadhey_session",MODE_PRIVATE).edit().putBoolean("keep_signed_in",keepSignedIn!=null && keepSignedIn.isChecked()).apply();
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
            if(!Boolean.TRUE.equals(p.child("active").getValue(Boolean.class))){
                auth.signOut(); render(); if(message!=null) message.setText("This account is inactive. Contact the RAADHEY administrator."); return;
            }
            String role=Objects.toString(p.child("role").getValue(String.class),"");
            employeeName=Objects.toString(p.child("fullName").getValue(String.class),"Employee");
            if("admin".equals(role)){
                renderFounderDashboard();
                return;
            }
            if(!"employee".equals(role)){
                auth.signOut(); render(); if(message!=null) message.setText("This account does not have an authorised RAADHEY role."); return;
            }
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

    private void renderFounderDashboard() {
        root.removeAllViews();
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        TextView heading=label("FOUNDER DASHBOARD",20,GREEN,true);
        heading.setPadding(0,dp(12),0,dp(8)); root.addView(heading,full());
        TextView welcome=label("Welcome, "+employeeName,14,0xFF555555,false);
        welcome.setPadding(0,0,0,dp(18)); root.addView(welcome,full());
        LinearLayout stats=new LinearLayout(this); stats.setOrientation(LinearLayout.VERTICAL);
        stats.setPadding(dp(14),dp(14),dp(14),dp(14)); stats.setBackgroundColor(0xFFFFFFFF);
        TextView employees=label("ACTIVE EMPLOYEES: Loading…",15,GREEN,true);
        TextView present=label("PRESENT TODAY: Loading…",15,GREEN,true);
        TextView onDuty=label("ON DUTY NOW: Loading…",15,GREEN,true);
        TextView late=label("LATE TODAY: Loading…",15,GREEN,true);
        stats.addView(employees,full()); stats.addView(present,full()); stats.addView(onDuty,full()); stats.addView(late,full());
        root.addView(stats,full());
        TextView listTitle=label("EMPLOYEE DIRECTORY",16,GREEN,true);
        listTitle.setPadding(0,dp(22),0,dp(10)); root.addView(listTitle,full());
        LinearLayout employeeList=new LinearLayout(this); employeeList.setOrientation(LinearLayout.VERTICAL);
        ScrollView listScroll=new ScrollView(this); listScroll.addView(employeeList);
        LinearLayout.LayoutParams listParams=full(); listParams.height=dp(280); root.addView(listScroll,listParams);
        Runnable refresh=()->{
            db.child("profiles").get().addOnSuccessListener(all->{
                int active=0; employeeList.removeAllViews();
                for(DataSnapshot item:all.getChildren()){
                    if(!"employee".equals(item.child("role").getValue(String.class))) continue;
                    boolean enabled=Boolean.TRUE.equals(item.child("active").getValue(Boolean.class));
                    if(enabled) active++;
                    String name=Objects.toString(item.child("fullName").getValue(String.class),"Employee");
                    String code=Objects.toString(item.child("employeeCode").getValue(String.class),"");
                    TextView row=label(name+"  ·  "+code+"  ·  "+(enabled?"ACTIVE":"INACTIVE")+"  ›",13,enabled?GREEN:0xFFB42318,true);
                    row.setPadding(dp(10),dp(12),dp(10),dp(12));
                    row.setOnClickListener(v->{
                        String action=enabled?"Deactivate access":"Reactivate access";
                        new android.app.AlertDialog.Builder(this)
                            .setTitle(name+" · "+code)
                            .setMessage(enabled?"Remove this employee's app access? Their attendance history will be retained.":"Restore this employee's app access?")
                            .setNegativeButton("CANCEL",null)
                            .setPositiveButton(action,(d,w)->db.child("profiles").child(item.getKey()).child("active").setValue(!enabled)
                                .addOnSuccessListener(done->{toast(enabled?"Employee access deactivated. Attendance history retained.":"Employee access restored.");renderFounderDashboard();})
                                .addOnFailureListener(err->toast("Could not update employee: "+err.getLocalizedMessage())))
                            .show();
                    });
                    employeeList.addView(row,full());
                }
                employees.setText("ACTIVE EMPLOYEES: "+active);
                db.child("attendance").get().addOnSuccessListener(records->{
                    int total=0,working=0,lateCount=0;
                    for(DataSnapshot person:records.getChildren()){
                        DataSnapshot today=person.child(dateKey());
                        if(today.exists()){
                            total++;
                            if(!today.child("checkOutAt").exists()) working++;
                            if(Boolean.TRUE.equals(today.child("late").getValue(Boolean.class))) lateCount++;
                        }
                    }
                    present.setText("PRESENT TODAY: "+total);
                    onDuty.setText("ON DUTY NOW: "+working);
                    late.setText("LATE TODAY: "+lateCount);
                }).addOnFailureListener(e->present.setText("Attendance unavailable: "+e.getLocalizedMessage()));
            }).addOnFailureListener(e->employees.setText("Unable to load employees: "+e.getLocalizedMessage()));
        };
        Button addEmployee=button("＋ ADD EMPLOYEE");
        LinearLayout.LayoutParams addParams=full(); addParams.topMargin=dp(18); root.addView(addEmployee,addParams);
        addEmployee.setOnClickListener(v->showCreateEmployeeDialog());
        Button reload=button("REFRESH DASHBOARD"); LinearLayout.LayoutParams rp=full(); rp.topMargin=dp(16); root.addView(reload,rp); reload.setOnClickListener(v->refresh.run());
        Button logout=button("SIGN OUT"); logout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF777777));
        LinearLayout.LayoutParams lp=full(); lp.topMargin=dp(10); root.addView(logout,lp);
        logout.setOnClickListener(v->{auth.signOut(); attendanceState=0; render();});
        TextView note=label("Founder access · Employee attendance and directory overview",11,0xFF777777,false);
        note.setPadding(0,dp(16),0,0); root.addView(note,full());
        refresh.run();
    }

    private void showCreateEmployeeDialog(){
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20),dp(8),dp(20),0);
        EditText name=new EditText(this); name.setHint("Employee full name"); form.addView(name,full());
        EditText code=new EditText(this); code.setHint("Employee ID (e.g. TEST001)"); form.addView(code,full());
        EditText password=new EditText(this); password.setHint("Temporary password (min. 6 characters)"); password.setInputType(129); form.addView(password,full());
        EditText department=new EditText(this); department.setHint("Department (optional)"); form.addView(department,full());
        new android.app.AlertDialog.Builder(this).setTitle("Create employee account").setView(form)
            .setNegativeButton("CANCEL",null).setPositiveButton("CREATE",null).create();
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Create employee account").setView(form).setNegativeButton("CANCEL",null).setPositiveButton("CREATE",null).create();
        dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{
            String fullName=name.getText().toString().trim(), employeeId=code.getText().toString().trim().toUpperCase(Locale.US), pass=password.getText().toString();
            if(fullName.isEmpty()||employeeId.isEmpty()||pass.length()<6){toast("Enter name, employee ID and a password of at least 6 characters.");return;}
            dialog.getButton(-1).setEnabled(false);
            createEmployeeAccount(fullName,employeeId,pass,department.getText().toString().trim(),dialog);
        }));
        dialog.show();
    }

    private void createEmployeeAccount(String fullName,String code,String password,String department,android.app.AlertDialog dialog){
        String email=code.toLowerCase(Locale.US)+"@employees.raadhey.com";
        DatabaseReference profiles=db.child("profiles");
        profiles.get().addOnCompleteListener(this,check->{
            if(!check.isSuccessful() || check.getResult()==null){
                dialog.getButton(-1).setEnabled(true);
                Exception error=check.getException();
                toast("Employee lookup failed: "+(error==null?"Unknown Firebase error":error.getLocalizedMessage()));
                return;
            }
            boolean duplicate=false;
            for(DataSnapshot existing:check.getResult().getChildren()){
                if(code.equalsIgnoreCase(Objects.toString(existing.child("employeeCode").getValue(String.class),""))){duplicate=true;break;}
            }
            if(duplicate){dialog.getButton(-1).setEnabled(true);toast("Employee ID already exists.");return;}
            FirebaseApp app;
            try{app=FirebaseApp.getInstance("raadheyEmployeeCreator");}
            catch(IllegalStateException ex){app=FirebaseApp.initializeApp(this,FirebaseApp.getInstance().getOptions(),"raadheyEmployeeCreator");}
            FirebaseAuth creator=FirebaseAuth.getInstance(app);
            creator.createUserWithEmailAndPassword(email,password).addOnCompleteListener(this,created->{
                if(!created.isSuccessful()){dialog.getButton(-1).setEnabled(true);toast("Account creation failed: "+(created.getException()==null?"Unknown error":created.getException().getLocalizedMessage()));return;}
                FirebaseUser newUser=created.getResult().getUser();
                Map<String,Object> profile=new HashMap<>();
                profile.put("fullName",fullName);profile.put("employeeCode",code);profile.put("role","employee");profile.put("active",true);profile.put("department",department);profile.put("createdAt",ServerValue.TIMESTAMP);
                profiles.child(newUser.getUid()).setValue(profile).addOnCompleteListener(this,saved->{
                    creator.signOut();
                    if(saved.isSuccessful()){dialog.dismiss();toast("Employee "+code+" created successfully.");renderFounderDashboard();}
                    else{dialog.getButton(-1).setEnabled(true);toast("Profile save failed: "+(saved.getException()==null?"Database error":saved.getException().getLocalizedMessage()));}
                });
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
        if(accuracy>150){ locationInfo.setTextColor(0xFFB42318); locationInfo.setText("GPS accuracy is "+Math.round(accuracy)+"m. Please retry outdoors."); return; }
        if(!isInsideFactoryBoundary(loc.getLatitude(),loc.getLongitude())){ locationInfo.setTextColor(0xFFB42318); locationInfo.setText("⚠ You are outside the RAADHEY factory attendance boundary."); return; }
        locationInfo.setTextColor(GREEN);
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
