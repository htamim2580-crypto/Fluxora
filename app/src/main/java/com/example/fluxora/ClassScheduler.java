package com.example.fluxora;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.RoutineEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class ClassScheduler {

    // 0 = notify when the class starts. Set to 5 to be asked 5 minutes before.
    private static final int LEAD_MINUTES = 0;
    // Quizzes and other timed notes: remind this many minutes before.
    private static final int QUIZ_LEAD_MINUTES = 30;
    // Assignments and deadlines: remind at this hour the evening before.
    private static final int REMINDER_HOUR = 20;

    public static void scheduleAllAsync(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppDatabase.dbExecutor.execute(() -> {
            for (RoutineEvent e : AppDatabase.get(app).routineDao().getAllNow()) {
                scheduleEvent(app, e);
            }
        });
    }

    /** Sets the alarm for this item's next occurrence (weekly classes) or its date (one-off items). */
    public static void scheduleEvent(Context ctx, RoutineEvent e) {
        if ("CANCELLED".equals(e.type)) return;

        ZonedDateTime trigger;
        String date;

        if (e.dayOfWeek >= 1 && e.dayOfWeek <= 7) {
            // Weekly class
            LocalTime time = parseTime(e.startTime);
            if (time == null) return;
            ZonedDateTime now = ZonedDateTime.now();
            int todayNum = now.getDayOfWeek().getValue() % 7 + 1;      // 1 = Sunday
            int diff = (e.dayOfWeek - todayNum + 7) % 7;
            ZonedDateTime start = now.toLocalDate().plusDays(diff).atTime(time).atZone(now.getZone());
            if (!start.minusMinutes(LEAD_MINUTES).isAfter(now)) start = start.plusWeeks(1);
            trigger = start.minusMinutes(LEAD_MINUTES);
            date = start.toLocalDate().toString();
        } else if (e.date != null) {
            // One-off dated item
            trigger = oneOffTrigger(e);
            if (trigger == null) return;
            date = e.date;
        } else {
            return;
        }

        setAlarm(ctx, e.id, date, trigger);
    }

    /** When to remind about a dated item, or null if that moment has already passed. */
    private static ZonedDateTime oneOffTrigger(RoutineEvent e) {
        LocalDate day;
        try {
            day = LocalDate.parse(e.date);
        } catch (Exception ex) {
            return null;
        }
        LocalTime time = parseTime(e.startTime);
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime t;

        if ("EXTRA_CLASS".equals(e.type) && time != null) {
            t = day.atTime(time).atZone(zone).minusMinutes(LEAD_MINUTES);
        } else if (time != null && !"ASSIGNMENT".equals(e.type) && !"DEADLINE".equals(e.type)) {
            t = day.atTime(time).atZone(zone).minusMinutes(QUIZ_LEAD_MINUTES);
        } else {
            t = day.minusDays(1).atTime(REMINDER_HOUR, 0).atZone(zone);
        }
        return t.isAfter(ZonedDateTime.now(zone)) ? t : null;
    }

    private static void setAlarm(Context ctx, long eventId, String date, ZonedDateTime trigger) {
        Intent i = new Intent(ctx, ClassAlarmReceiver.class);
        i.putExtra("eventId", eventId);
        i.putExtra("date", date);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) eventId, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        long at = trigger.toInstant().toEpochMilli();
        boolean exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException ex) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static void cancel(Context ctx, long eventId) {
        Intent i = new Intent(ctx, ClassAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) eventId, i,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            ctx.getSystemService(AlarmManager.class).cancel(pi);
            pi.cancel();
        }
    }

    private static LocalTime parseTime(String s) {
        if (s == null) return null;
        try {
            String[] p = s.trim().split(":");
            return LocalTime.of(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()));
        } catch (Exception ex) {
            return null;
        }
    }
}