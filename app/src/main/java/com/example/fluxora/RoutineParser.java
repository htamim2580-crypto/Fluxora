package com.example.fluxora;

import com.example.fluxora.db.RoutineEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RoutineParser {

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            "CLASS", "EXTRA_CLASS", "CANCELLED", "ASSIGNMENT", "DEADLINE", "OTHER"));

    private static String str(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        return o.optString(key, "").trim();
    }

    /** Returns null if the reply isn't valid JSON. */
    public static List<RoutineEvent> tryParse(String json, String source) {
        try {
            String clean = json.trim().replaceAll("^```(?:json)?|```$", "").trim();
            JSONArray arr = new JSONArray(clean);
            List<RoutineEvent> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                RoutineEvent e = new RoutineEvent();
                e.title = str(o, "title");
                if (e.title.isEmpty()) e.title = "Untitled";
                String type = str(o, "type").toUpperCase(Locale.US);
                e.type = type.isEmpty() ? "CLASS" : (TYPES.contains(type) ? type : "OTHER");
                e.startTime = str(o, "startTime");
                e.endTime = str(o, "endTime");
                e.room = str(o, "room");
                e.details = str(o, "details");
                e.source = source;
                e.createdAt = System.currentTimeMillis();

                String date = str(o, "date");
                if (!date.isEmpty()) {
                    try {
                        LocalDate.parse(date);
                        e.date = date;
                        e.dayOfWeek = 0;
                    } catch (Exception ignore) { }
                }
                if (e.date == null) e.dayOfWeek = o.optInt("dayOfWeek", 0);
                if (e.date == null && (e.dayOfWeek < 1 || e.dayOfWeek > 7)) continue;   // nowhere to show it
                list.add(e);
            }
            return list;
        } catch (Exception ex) {
            return null;
        }
    }
}