package com.example.fluxora;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.RoutineEvent;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public class ClassScheduler {

    // 0 = notify when the class starts. Set to 5 to be asked 5 minutes before.
    private static final int LEAD_MINUTES = 0;

    public static void scheduleAllAsync(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppDatabase.dbExecutor.execute(() -> {
            for (RoutineEvent e : AppDatabase.get(app).routineDao().getAllNow()) {
                scheduleEvent(app, e);
            }
        });
    }

    /** Sets the alarm for this class's next occurrence. */
    public static void scheduleEvent(Context ctx, RoutineEvent e) {
        if (e.dayOfWeek < 1 || e.dayOfWeek > 7) return;
        LocalTime time = parseTime(e.startTime);
        if (time == null) return;

        ZonedDateTime now = ZonedDateTime.now();
        int todayNum = now.getDayOfWeek().getValue() % 7 + 1;      // 1 = Sunday
        int diff = (e.dayOfWeek - todayNum + 7) % 7;
        ZonedDateTime start = now.toLocalDate().plusDays(diff).atTime(time).atZone(now.getZone());
        if (!start.minusMinutes(LEAD_MINUTES).isAfter(now)) start = start.plusWeeks(1);
        ZonedDateTime trigger = start.minusMinutes(LEAD_MINUTES);

        Intent i = new Intent(ctx, ClassAlarmReceiver.class);
        i.putExtra("eventId", e.id);
        i.putExtra("date", start.toLocalDate().toString());
        PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) e.id, i,
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