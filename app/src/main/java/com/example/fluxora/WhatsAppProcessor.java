package com.example.fluxora;

import android.content.Context;
import android.util.Log;
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.RoutineDao;
import com.example.fluxora.db.RoutineEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WhatsAppProcessor {

    private static final Pattern CODE =
            Pattern.compile("(CSE|ECE|MATH|HUM)\\s?-?\\s?(\\d{4})", Pattern.CASE_INSENSITIVE);

    public static void process(Context ctx, String chat, String text) {
        Context app = ctx.getApplicationContext();
        AppDatabase.dbExecutor.execute(() -> {
            List<RoutineEvent> known = AppDatabase.get(app).routineDao().getAllNow();
            String prompt = PromptBuilder.update(app, text, known, true);
            AiClient.ask(prompt, null, new AiClient.Callback() {
                @Override public void onSuccess(String reply) {
                    save(app, reply, known, chat);
                }
                @Override public void onError(String message) {
                    Log.w("Fluxora", "WhatsApp AI error: " + message);
                }
            });
        });
    }

    private static void save(Context app, String reply, List<RoutineEvent> known, String chat) {
        AppDatabase.dbExecutor.execute(() -> {
            List<RoutineEvent> parsed = RoutineParser.tryParse(reply, "WHATSAPP");
            if (parsed == null || parsed.isEmpty()) return;

            List<String> titles = new ArrayList<>();
            for (RoutineEvent k : known) {
                if (k.dayOfWeek >= 1 && k.dayOfWeek <= 7 && !titles.contains(k.title)) titles.add(k.title);
            }

            RoutineDao dao = AppDatabase.get(app).routineDao();
            List<RoutineEvent> added = new ArrayList<>();
            for (RoutineEvent e : parsed) {
                if (e.date == null) continue;                       // WhatsApp items need a date
                if ("EXTRA_CLASS".equals(e.type) || "CANCELLED".equals(e.type)) {
                    e.title = matchTitle(e.title, titles);     // only these must match a saved class exactly
                }
                String start = e.startTime == null ? "" : e.startTime;
                if (dao.countSame(e.type, e.title, e.date, start) > 0) continue;   // already added
                e.id = dao.insert(e);
                ClassScheduler.scheduleEvent(app, e);               // alarm works even if the app is closed
                added.add(e);
            }
            if (!added.isEmpty()) NotificationHelper.showWhatsAppAdded(app, added, chat);
        });
    }

    /** Maps "MATH-2207" or "math 2207" in the AI's title onto the exact saved course title. */
    private static String matchTitle(String title, List<String> known) {
        Matcher m = CODE.matcher(title);
        if (!m.find()) return title;
        String code = (m.group(1) + " " + m.group(2)).toUpperCase(Locale.US);
        for (String k : known) {
            if (k.toUpperCase(Locale.US).contains(code)) return k;
        }
        return title;
    }
}