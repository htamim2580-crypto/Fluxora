package com.example.fluxora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import com.example.fluxora.db.RoutineEvent;

public class NotificationHelper {
    public static final String CHANNEL_ID = "class_reminders";

    public static void createChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Class reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Asks whether you are attending when a class starts");
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    public static void showClassNotification(Context ctx, RoutineEvent e, String date) {
        int notifId = (int) e.id;
        PendingIntent yes = actionIntent(ctx, e, date, true, notifId, (int) (e.id * 2));
        PendingIntent no = actionIntent(ctx, e, date, false, notifId, (int) (e.id * 2 + 1));
        PendingIntent open = PendingIntent.getActivity(ctx, notifId,
                new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String time = e.startTime + (e.endTime != null && !e.endTime.isEmpty() ? " – " + e.endTime : "");
        String where = (e.room != null && !e.room.isEmpty()) ? " · Room " + e.room : "";

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(e.title)
                .setContentText("Class started. Are you attending?")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(time + where + "\nAre you attending?"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, "Yes, attending", yes)
                .addAction(0, "No", no);

        ctx.getSystemService(NotificationManager.class).notify(notifId, b.build());
    }

    public static void showConfirmation(Context ctx, int notifId, String title, boolean present) {
        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(present ? "Marked present" : "Marked absent")
                .setContentText(title)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(4000)
                .setAutoCancel(true)
                .build();
        ctx.getSystemService(NotificationManager.class).notify(notifId, n);
    }

    private static PendingIntent actionIntent(Context ctx, RoutineEvent e, String date,
                                              boolean present, int notifId, int requestCode) {
        Intent i = new Intent(ctx, AttendanceActionReceiver.class);
        i.putExtra("title", e.title);
        i.putExtra("date", date);
        i.putExtra("startTime", e.startTime);
        i.putExtra("present", present);
        i.putExtra("notifId", notifId);
        return PendingIntent.getBroadcast(ctx, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}