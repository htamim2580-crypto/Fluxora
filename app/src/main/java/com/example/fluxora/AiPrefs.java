package com.example.fluxora;

import android.content.Context;
import android.content.SharedPreferences;

public class AiPrefs {

    private static SharedPreferences sp;

    public static void init(Context ctx) {
        if (sp == null) {
            sp = ctx.getApplicationContext().getSharedPreferences("fluxora_ai", Context.MODE_PRIVATE);
        }
    }

    public static boolean enabled() {
        return sp == null || sp.getBoolean("enabled", true);
    }

    public static void setEnabled(boolean on) {
        if (sp != null) sp.edit().putBoolean("enabled", on).apply();
    }

    public static String userKey() {
        return sp == null ? "" : sp.getString("key", "");
    }

    public static void setUserKey(String key) {
        if (sp != null) sp.edit().putString("key", key == null ? "" : key.trim()).apply();
    }

    public static boolean usingOwnKey() {
        return !userKey().isEmpty();
    }

    /** The user's own key if they added one, otherwise the key built into the app. */
    public static String effectiveKey() {
        String k = userKey();
        return k.isEmpty() ? BuildConfig.GEMINI_API_KEY : k;
    }
}