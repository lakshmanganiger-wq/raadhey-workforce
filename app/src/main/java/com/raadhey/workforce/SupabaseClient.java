package com.raadhey.workforce;

import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class SupabaseClient {
    private SupabaseClient() {}
    public static final String BASE_URL = BuildConfig.SUPABASE_URL;
    public static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;

    public static JSONObject request(String path, String method, String accessToken, JSONObject body) throws Exception {
        if (BASE_URL == null || BASE_URL.trim().isEmpty() || ANON_KEY == null || ANON_KEY.trim().isEmpty()) {
            throw new IllegalStateException("App backend is not configured yet. Add the Supabase project URL and anon key.");
        }
        URL url = new URL(BASE_URL.replaceAll("/+$", "") + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("apikey", ANON_KEY);
        c.setRequestProperty("Accept", "application/json");
        if (accessToken != null && !accessToken.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + accessToken);
        else c.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        }
        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder response = new StringBuilder();
        if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) response.append(line);
        }
        c.disconnect();
        if (code < 200 || code >= 300) throw new IOException(response.length() == 0 ? "Server request failed (" + code + ")" : response.toString());
        String s = response.toString().trim();
        if (s.isEmpty()) return new JSONObject();
        if (s.startsWith("{")) return new JSONObject(s);
        JSONObject result = new JSONObject(); result.put("raw", s); return result;
    }

    public static JSONObject signIn(String email, String password) throws Exception {
        JSONObject body = new JSONObject(); body.put("email", email); body.put("password", password);
        return request("/auth/v1/token?grant_type=password", "POST", null, body);
    }

    public static JSONObject rpc(String name, String token, double lat, double lng, float accuracy) throws Exception {
        JSONObject body = new JSONObject();
        body.put("p_latitude", lat); body.put("p_longitude", lng); body.put("p_accuracy_m", accuracy);
        return request("/rest/v1/rpc/" + name, "POST", token, body);
    }

    public static void sendLocation(String token, String userId, double lat, double lng, float accuracy) throws Exception {
        JSONObject row = new JSONObject();
        row.put("employee_id", userId); row.put("latitude", lat); row.put("longitude", lng);
        row.put("accuracy_m", accuracy); row.put("recorded_at", java.time.Instant.now().toString());
        URL url = new URL(BASE_URL.replaceAll("/+$", "") + "/rest/v1/employee_locations");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        c.setRequestProperty("apikey", ANON_KEY);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Prefer", "return=minimal");
        try (OutputStream out = c.getOutputStream()) { out.write(row.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("Location sync failed (" + code + ")");
        c.disconnect();
    }
}
