package com.example.fluxora;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.Attendance;
import com.example.fluxora.db.RoutineEvent;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CloudBackup {

    public interface Callback {
        void onDone(String message);
        void onError(String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static DocumentReference doc(FirebaseUser user, String kind) {
        return FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("backups").document(kind);
    }

    // ---------- backup ----------

    public static void backup(Context ctx, Callback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            cb.onError("Sign in first");
            return;
        }
        Context app = ctx.getApplicationContext();

        AppDatabase.dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.get(app);

            List<Map<String, Object>> routine = new ArrayList<>();
            for (RoutineEvent e : db.routineDao().getAllNow()) {
                Map<String, Object> m = new HashMap<>();
                m.put("title", e.title);
                m.put("type", e.type);
                m.put("dayOfWeek", e.dayOfWeek);
                m.put("date", e.date);
                m.put("startTime", e.startTime);
                m.put("endTime", e.endTime);
                m.put("room", e.room);
                m.put("details", e.details);
                m.put("source", e.source);
                m.put("createdAt", e.createdAt);
                routine.add(m);
            }

            List<Map<String, Object>> attendance = new ArrayList<>();
            for (Attendance a : db.attendanceDao().getAllNow()) {
                Map<String, Object> m = new HashMap<>();
                m.put("courseTitle", a.courseTitle);
                m.put("date", a.date);
                m.put("startTime", a.startTime);
                m.put("present", a.present);
                attendance.add(m);
            }

            Map<String, Object> r = new HashMap<>();
            r.put("items", routine);
            r.put("updatedAt", FieldValue.serverTimestamp());

            Map<String, Object> a = new HashMap<>();
            a.put("items", attendance);
            a.put("updatedAt", FieldValue.serverTimestamp());

            Task<Void> t1 = doc(user, "routine").set(r);
            Task<Void> t2 = doc(user, "attendance").set(a);
            Tasks.whenAll(t1, t2)
                    .addOnSuccessListener(v -> cb.onDone("Backed up " + routine.size()
                            + " routine items and " + attendance.size() + " attendance records"))
                    .addOnFailureListener(ex -> cb.onError(ex.getMessage()));
        });
    }

    // ---------- restore ----------

    public static void restore(Context ctx, Callback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            cb.onError("Sign in first");
            return;
        }
        Context app = ctx.getApplicationContext();

        Task<DocumentSnapshot> t1 = doc(user, "routine").get();
        Task<DocumentSnapshot> t2 = doc(user, "attendance").get();
        Tasks.whenAllSuccess(t1, t2)
                .addOnSuccessListener(results -> {
                    DocumentSnapshot r = (DocumentSnapshot) results.get(0);
                    DocumentSnapshot a = (DocumentSnapshot) results.get(1);
                    if (!r.exists() && !a.exists()) {
                        cb.onError("No backup found for this account");
                        return;
                    }
                    AppDatabase.dbExecutor.execute(() -> {
                        try {
                            int[] counts = apply(app, r, a);
                            ClassScheduler.scheduleAllAsync(app);
                            MAIN.post(() -> cb.onDone("Restored " + counts[0]
                                    + " routine items and " + counts[1] + " attendance records"));
                        } catch (Exception ex) {
                            MAIN.post(() -> cb.onError(ex.getMessage()));
                        }
                    });
                })
                .addOnFailureListener(ex -> cb.onError(ex.getMessage()));
    }

    /** Replaces local data with the cloud copy. Only a data type that has a backup is replaced. */
    private static int[] apply(Context app, DocumentSnapshot r, DocumentSnapshot a) {
        AppDatabase db = AppDatabase.get(app);
        List<Map<String, Object>> routineItems = items(r);
        List<Map<String, Object>> attendanceItems = items(a);
        boolean hasRoutine = r.exists();
        boolean hasAttendance = a.exists();

        db.runInTransaction(() -> {
            if (hasRoutine) {
                db.routineDao().deleteAll();
                for (Map<String, Object> m : routineItems) {
                    RoutineEvent e = new RoutineEvent();
                    e.title = str(m, "title");
                    e.type = str(m, "type");
                    e.dayOfWeek = (int) num(m, "dayOfWeek");
                    e.date = str(m, "date");
                    e.startTime = str(m, "startTime");
                    e.endTime = str(m, "endTime");
                    e.room = str(m, "room");
                    e.details = str(m, "details");
                    e.source = str(m, "source");
                    e.createdAt = num(m, "createdAt");
                    db.routineDao().insert(e);
                }
            }
            if (hasAttendance) {
                db.attendanceDao().deleteAll();
                for (Map<String, Object> m : attendanceItems) {
                    Attendance x = new Attendance();
                    x.courseTitle = str(m, "courseTitle");
                    x.date = str(m, "date");
                    x.startTime = str(m, "startTime");
                    x.present = Boolean.TRUE.equals(m.get("present"));
                    db.attendanceDao().upsert(x);
                }
            }
        });
        return new int[]{hasRoutine ? routineItems.size() : 0, hasAttendance ? attendanceItems.size() : 0};
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(DocumentSnapshot d) {
        if (d == null || !d.exists()) return new ArrayList<>();
        Object o = d.get("items");
        return o == null ? new ArrayList<>() : (List<Map<String, Object>>) o;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    private static long num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }
}