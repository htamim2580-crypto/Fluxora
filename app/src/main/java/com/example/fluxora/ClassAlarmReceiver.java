package com.example.fluxora;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.RoutineEvent;

public class ClassAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long eventId = intent.getLongExtra("eventId", -1);
        String date = intent.getStringExtra("date");
        if (eventId < 0 || date == null) return;

        PendingResult result = goAsync();
        AppDatabase.dbExecutor.execute(() -> {
            try {
                RoutineEvent e = AppDatabase.get(context).routineDao().getById(eventId);
                if (e == null) return;                                   // item was deleted

                if (e.dayOfWeek >= 1 && e.dayOfWeek <= 7) {
                    // Weekly class: skip if it was cancelled for this date, then set next week's alarm
                    boolean cancelled = AppDatabase.get(context).routineDao()
                            .countCancelled(e.title, date) > 0;
                    if (!cancelled) NotificationHelper.showClassNotification(context, e, date);
                    ClassScheduler.scheduleEvent(context, e);
                } else if ("EXTRA_CLASS".equals(e.type)) {
                    NotificationHelper.showClassNotification(context, e, date);
                } else {
                    NotificationHelper.showReminder(context, e);
                }
            } finally {
                result.finish();
            }
        });
    }
}