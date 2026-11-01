package com.example.fluxora;

import android.content.Context;
import android.content.SharedPreferences;

public class UserProfile {
    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("fluxora_profile", Context.MODE_PRIVATE);
    }

    public static boolean isSet(Context c) { return p(c).contains("group"); }

    public static void save(Context c, String roll, String group, String phone) {
        p(c).edit().putString("roll", roll).putString("group", group).putString("phone", phone).apply();
    }

    public static String roll(Context c)  { return p(c).getString("roll", ""); }
    public static String group(Context c) { return p(c).getString("group", ""); }   // A1, A2, B1, B2
    public static String phone(Context c) { return p(c).getString("phone", ""); }

    public static String section(Context c) {
        String g = group(c);
        return g.isEmpty() ? "" : g.substring(0, 1);                                // A or B
    }
}