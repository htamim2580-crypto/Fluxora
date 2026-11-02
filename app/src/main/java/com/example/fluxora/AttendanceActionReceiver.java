package com.example.fluxora;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.Attendance;

public class AttendanceActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String date = intent.getStringExtra("date");
        String startTime = intent.getStringExtra("startTime");
        boolean present = intent.getBooleanExtra("present", false);
        int notifId = intent.getIntExtra("notifId", 0);
        if (title == null || date == null) return;

        PendingResult result = goAsync();
        AppDatabase.dbExecutor.execute(() -> {
            try {
                Attendance a = new Attendance();
                a.courseTitle = title;
                a.date = date;
                a.startTime = startTime;
                a.present = present;
                AppDatabase.get(context).attendanceDao().upsert(a);

                NotificationHelper.showConfirmation(context, notifId, title, present);
            } finally {
                result.finish();
            }
        });
    }
}