package com.example.fluxora;

import com.example.fluxora.db.RoutineEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class RoutineParser {

    /** Returns null if the reply isn't valid JSON. */
    public static List<RoutineEvent> tryParse(String json, String source) {
        try {
            String clean = json.trim().replaceAll("^```(?:json)?|```$", "").trim();
            JSONArray arr = new JSONArray(clean);
            List<RoutineEvent> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                RoutineEvent e = new RoutineEvent();
                e.title = o.optString("title", "Untitled");
                e.type = "CLASS";
                e.dayOfWeek = o.optInt("dayOfWeek", 0);
                e.startTime = o.optString("startTime", "");
                e.endTime = o.optString("endTime", "");
                e.room = o.optString("room", "");
                e.details = o.optString("details", "");
                e.source = source;
                e.createdAt = System.currentTimeMillis();
                list.add(e);
            }
            return list;
        } catch (Exception ex) {
            return null;
        }
    }
}