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
                if (e == null) return;                         // class was deleted
                NotificationHelper.showClassNotification(context, e, date);
                ClassScheduler.scheduleEvent(context, e);      // next week's alarm
            } finally {
                result.finish();
            }
        });
    }
}