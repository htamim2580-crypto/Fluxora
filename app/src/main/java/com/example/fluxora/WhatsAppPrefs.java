package com.example.fluxora;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WhatsAppPrefs {

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("fluxora_whatsapp", Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) {
        return p(c).getBoolean("enabled", true);
    }

    public static void setEnabled(Context c, boolean on) {
        p(c).edit().putBoolean("enabled", on).apply();
    }

    public static Set<String> selected(Context c) {
        return new HashSet<>(p(c).getStringSet("selected", new HashSet<>()));
    }

    public static void setSelected(Context c, Set<String> s) {
        p(c).edit().putStringSet("selected", new HashSet<>(s)).apply();
    }

    /** Recently seen chat names, newest first. Kept only on this phone so you can pick your class groups. */
    public static List<String> seenChats(Context c) {
        String raw = p(c).getString("seen", "");
        List<String> list = new ArrayList<>();
        if (!raw.isEmpty()) list.addAll(Arrays.asList(raw.split("\n")));
        return list;
    }

    public static void rememberChat(Context c, String name) {
        List<String> list = seenChats(c);
        list.remove(name);
        list.add(0, name);
        while (list.size() > 30) list.remove(list.size() - 1);
        p(c).edit().putString("seen", String.join("\n", list)).apply();
    }
}