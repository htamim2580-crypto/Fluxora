package com.example.fluxora;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WhatsAppListener extends NotificationListenerService {

    private static final Set<String> PACKAGES =
            new HashSet<>(Arrays.asList("com.whatsapp", "com.whatsapp.w4b"));
    private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;

    // Messages already handled, so none is processed twice.
    private final LinkedHashSet<String> recent = new LinkedHashSet<>();

    private static class Msg {
        final String text;
        final long time;
        Msg(String text, long time) {
            this.text = text;
            this.time = time;
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !PACKAGES.contains(sbn.getPackageName())) return;
        if (!WhatsAppPrefs.enabled(this)) return;

        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle ex = n.extras;
        CharSequence titleCs = ex.getCharSequence(Notification.EXTRA_TITLE);
        if (titleCs == null) return;
        String chat = titleCs.toString().replaceAll("\\s*\\(\\d+ messages?\\)\\s*$", "").trim();
        if (chat.isEmpty()) return;

        WhatsAppPrefs.rememberChat(this, chat);                       // so it shows up in the picker
        if (!WhatsAppPrefs.selected(this).contains(chat)) return;     // only your class groups

        List<Msg> msgs = new ArrayList<>();
        NotificationCompat.MessagingStyle style =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);
        if (style != null) {
            for (NotificationCompat.MessagingStyle.Message m : style.getMessages()) {
                if (m.getText() != null) msgs.add(new Msg(m.getText().toString(), m.getTimestamp()));
            }
        }
        if (msgs.isEmpty()) {
            CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
            CharSequence text = big != null ? big : ex.getCharSequence(Notification.EXTRA_TEXT);
            if (text != null) msgs.add(new Msg(text.toString(), sbn.getPostTime()));
        }

        long now = System.currentTimeMillis();
        for (Msg m : msgs) {
            String text = m.text.trim();
            if (text.isEmpty() || text.matches("(?i)^\\d+ new messages?.*")) continue;
            if (now - m.time > MAX_AGE_MS) continue;                  // too old to read "tomorrow" safely

            if (!recent.add(chat + "|" + text)) continue;
            while (recent.size() > 200) {
                Iterator<String> it = recent.iterator();
                it.next();
                it.remove();
            }

            if (!RelevanceRules.looksRelevant(text)) continue;        // rules first, AI only if needed
            WhatsAppProcessor.process(getApplicationContext(), chat, text);
        }
    }
    @Override
    public void onCreate() {
        super.onCreate();
        AiPrefs.init(this);
    }
}