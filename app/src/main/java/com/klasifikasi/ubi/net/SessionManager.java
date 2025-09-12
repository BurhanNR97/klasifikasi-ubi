package com.klasifikasi.ubi.net;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private final SharedPreferences sp;
    public SessionManager(Context ctx) {
        sp = ctx.getSharedPreferences("session_ubi", Context.MODE_PRIVATE);
    }
    public void saveToken(String token) {
        sp.edit().putString("auth_token", token).apply();
    }
    public String getToken() {
        return sp.getString("auth_token", null);
    }
    public void clear() { sp.edit().clear().apply(); }
}
