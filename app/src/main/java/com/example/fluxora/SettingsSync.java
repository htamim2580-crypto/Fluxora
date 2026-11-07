package com.example.fluxora;

import android.content.Context;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SettingsSync {

    public interface Callback {
        void onDone();
        void onError(String message);
    }

    private static DatabaseReference ref(FirebaseUser user) {
        return FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
    }

    public static void push(Context ctx, Callback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            cb.onError("Sign in first");
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("roll", UserProfile.roll(ctx));
        data.put("group", UserProfile.group(ctx));
        data.put("whatsappEnabled", WhatsAppPrefs.enabled(ctx));
        data.put("whatsappChats", new ArrayList<>(WhatsAppPrefs.selected(ctx)));
        data.put("updatedAt", ServerValue.TIMESTAMP);
        data.put("aiEnabled", AiPrefs.enabled());

        ref(user).child("settings").setValue(data)
                .addOnSuccessListener(v -> cb.onDone())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void pull(Context ctx, Callback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            cb.onError("Sign in first");
            return;
        }
        Context app = ctx.getApplicationContext();
        ref(user).child("settings").get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) {
                        cb.onDone();
                        return;
                    }
                    String roll = snap.child("roll").getValue(String.class);
                    String group = snap.child("group").getValue(String.class);
                    if (roll != null && !roll.isEmpty() && group != null && !group.isEmpty()) {
                        UserProfile.save(app, roll, group, UserProfile.phone(app));   // phone stays local
                    }
                    Boolean enabled = snap.child("whatsappEnabled").getValue(Boolean.class);
                    if (enabled != null) WhatsAppPrefs.setEnabled(app, enabled);

                    Set<String> chats = new HashSet<>();
                    for (DataSnapshot c : snap.child("whatsappChats").getChildren()) {
                        String s = c.getValue(String.class);
                        if (s != null) chats.add(s);
                    }
                    WhatsAppPrefs.setSelected(app, chats);
                    cb.onDone();
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }
}