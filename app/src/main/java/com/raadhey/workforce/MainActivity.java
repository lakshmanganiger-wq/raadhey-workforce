package com.raadhey.workforce;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION_REQUEST = 41;
    private static final int EMERALD = 0xFF075B46;
    private static final int IVORY = 0xFFF7F5EF;
    private static final int GOLD = 0xFF9A783D;
    private Typeface lexend;
    private TextView statusText;
    private TextView locationText;
    private EditText employeeIdInput;
    private Button attendanceButton;
    private boolean checkedIn = false;
    private LocationManager locationManager;
    private SharedPreferences preferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            lexend = Typeface.createFromAsset(getAssets(), "Lexend.ttf");
        } catch (Exception ignored) {
            lexend = Typeface.create("sans-serif", Typeface.NORMAL);
        }
        preferences = getSharedPreferences("raadhey_attendance", MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        checkedIn = preferences.getBoolean("checked_in", false);
        buildScreen();
        refreshStatus();
    }

    private void applyFont(TextView view) {
        if (lexend != null) view.setTypeface(lexend);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 30, 28, 26);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(IVORY);

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.raadhey.workforce.R.drawable.raadhey_logo);
        logo.setContentDescription("RAADHEY peacock feather logo");
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        logoParams.bottomMargin = dp(8);
        root.addView(logo, logoParams);

        TextView brand = new TextView(this);
        brand.setText("RAADHEY");
        brand.setTextSize(30);
        brand.setTextColor(EMERALD);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(0.08f);
        brand.setTypeface(lexend, Typeface.BOLD);
        root.addView(brand, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView company = new TextView(this);
        company.setText("TEXTILE PRINTING &\nAPPAREL SOLUTIONS");
        company.setTextSize(11);
        company.setTextColor(GOLD);
        company.setGravity(Gravity.CENTER);
        company.setLetterSpacing(0.07f);
        company.setLineSpacing(dp(3), 1.0f);
        company.setPadding(dp(8), dp(7), dp(8), dp(20));
        applyFont(company);
        root.addView(company, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView section = new TextView(this);
        section.setText("WORKFORCE ATTENDANCE");
        section.setTextSize(12);
        section.setTextColor(EMERALD);
        section.setGravity(Gravity.CENTER);
        section.setLetterSpacing(0.10f);
        section.setPadding(0, 0, 0, dp(25));
        applyFont(section);
        root.addView(section, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        employeeIdInput = new EditText(this);
        employeeIdInput.setSingleLine(true);
        employeeIdInput.setTextSize(15);
        employeeIdInput.setHint("Enter employee ID");
        employeeIdInput.setText(preferences.getString("employee_id", ""));
        applyFont(employeeIdInput);
        root.addView(employeeIdInput, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setTextSize(17);
        statusText.setTextColor(0xFF173D32);
        statusText.setPadding(0, dp(26), 0, dp(14));
        applyFont(statusText);
        root.addView(statusText, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        locationText = new TextView(this);
        locationText.setText("Your GPS location will appear here when you mark attendance.");
        locationText.setTextSize(13);
        locationText.setTextColor(0xFF555555);
        locationText.setPadding(0, dp(8), 0, dp(22));
        applyFont(locationText);
        root.addView(locationText, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        attendanceButton = new Button(this);
        attendanceButton.setTextColor(0xFFFFFFFF);
        attendanceButton.setBackgroundTintList(ColorStateList.valueOf(EMERALD));
        applyFont(attendanceButton);
        attendanceButton.setOnClickListener(v -> handleAttendance());
        root.addView(attendanceButton, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setText("Prototype build • Attendance is stored on this device only");
        note.setTextSize(10);
        note.setTextColor(0xFF777777);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(28), 0, 0);
        applyFont(note);
        root.addView(note, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams fullWidth(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private void refreshStatus() {
        statusText.setText(checkedIn ? "Status: CHECKED IN" : "Status: NOT CHECKED IN");
        attendanceButton.setText(checkedIn ? "CHECK OUT" : "CHECK IN");
    }

    private void handleAttendance() {
        String employeeId = employeeIdInput.getText().toString().trim();
        if (employeeId.isEmpty()) {
            employeeIdInput.setError("Enter your employee ID");
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        captureLocation(employeeId);
    }

    private void captureLocation(String employeeId) {
        try {
            String provider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (!locationManager.isProviderEnabled(provider)) {
                Toast.makeText(this, "Please enable Location on your phone.", Toast.LENGTH_LONG).show();
                return;
            }
            locationText.setText("Getting your location…");
            locationManager.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    saveAttendance(employeeId, location);
                }
                @Override public void onProviderDisabled(String p) {
                    Toast.makeText(MainActivity.this, "Location provider disabled.", Toast.LENGTH_SHORT).show();
                }
                @Override public void onProviderEnabled(String p) { }
                @Override public void onStatusChanged(String p, int s, Bundle e) { }
            }, Looper.getMainLooper());
        } catch (SecurityException ex) {
            Toast.makeText(this, "Location permission is required.", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, "Unable to get GPS. Please try again outdoors.", Toast.LENGTH_LONG).show();
        }
    }

    private void saveAttendance(String employeeId, Location location) {
        String action = checkedIn ? "CHECK_OUT" : "CHECK_IN";
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String record = action + " | " + employeeId + " | " + time
                + " | " + location.getLatitude() + "," + location.getLongitude()
                + " | accuracy " + Math.round(location.getAccuracy()) + "m";
        String oldRecords = preferences.getString("records", "");
        preferences.edit()
                .putString("employee_id", employeeId)
                .putString("records", oldRecords.isEmpty() ? record : oldRecords + "\n" + record)
                .putBoolean("checked_in", !checkedIn)
                .apply();
        checkedIn = !checkedIn;
        locationText.setText("Last record:\n" + record);
        refreshStatus();
        Toast.makeText(this, action.replace('_', ' ') + " saved on this device.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            boolean granted = false;
            for (int result : grantResults) if (result == PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) handleAttendance();
            else Toast.makeText(this, "Location permission was not granted.", Toast.LENGTH_LONG).show();
        }
    }
}