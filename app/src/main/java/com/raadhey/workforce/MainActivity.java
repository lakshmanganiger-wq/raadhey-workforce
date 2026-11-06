package com.raadhey.workforce;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION_REQUEST = 41;
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
        preferences = getSharedPreferences("raadhey_attendance", MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        checkedIn = preferences.getBoolean("checked_in", false);
        buildScreen();
        refreshStatus();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 28);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFFF7F5EF);

        TextView brand = new TextView(this);
        brand.setText("RAADHEY");
        brand.setTextSize(30);
        brand.setTextColor(0xFF075B46);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(0.12f);
        root.addView(brand, fullWidth(WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("WORKFORCE ATTENDANCE");
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF8A6D35);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 6, 0, 34);
        root.addView(subtitle, fullWidth(WRAP_CONTENT));

        employeeIdInput = new EditText(this);
        employeeIdInput.setSingleLine(true);
        employeeIdInput.setHint("Enter employee ID");
        employeeIdInput.setText(preferences.getString("employee_id", ""));
        root.addView(employeeIdInput, fullWidth(WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setTextSize(18);
        statusText.setTextColor(0xFF173D32);
        statusText.setPadding(0, 28, 0, 16);
        root.addView(statusText, fullWidth(WRAP_CONTENT));

        locationText = new TextView(this);
        locationText.setText("Your GPS location will appear here when you mark attendance.");
        locationText.setTextSize(14);
        locationText.setTextColor(0xFF555555);
        locationText.setPadding(0, 8, 0, 24);
        root.addView(locationText, fullWidth(WRAP_CONTENT));

        attendanceButton = new Button(this);
        attendanceButton.setTextColor(0xFFFFFFFF);
        attendanceButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF075B46));
        attendanceButton.setOnClickListener(v -> handleAttendance());
        root.addView(attendanceButton, fullWidth(WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setText("Prototype build • Attendance is stored on this device only");
        note.setTextSize(11);
        note.setTextColor(0xFF777777);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, 32, 0, 0);
        root.addView(note, fullWidth(WRAP_CONTENT));

        setContentView(root);
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
