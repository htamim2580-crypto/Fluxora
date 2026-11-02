package com.example.fluxora;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.fluxora.databinding.ActivityMainBinding;
import com.example.fluxora.databinding.DialogAddEventBinding;
import com.example.fluxora.databinding.DialogProfileBinding;
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.Attendance;
import com.example.fluxora.db.RoutineEvent;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });
    private ActivityMainBinding binding;
    private AppDatabase db;
    private EventAdapter adapter;
    private String routinePrompt;
    private List<RoutineEvent> allEvents = new ArrayList<>();
    private List<Attendance> attendanceList = new ArrayList<>();
    private int selectedDay;   // 1 = Sunday ... 7 = Saturday

    // Week starts on Sunday: dayOfWeek 1 = Sunday ... 7 = Saturday
    private static final String[] DAYS =
            {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    private static final String[] SHORT_DAYS =
            {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private final ActivityResultLauncher<String[]> smsPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.READ_SMS))) {
                    SmsImporter.importAll(this);
                }
            });

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) importRoutineFrom(uri);
            });

    private String buildRoutinePrompt(String section, String group) {
        return "This is a university class routine with two sections (SEC - A and SEC - B), "
                + "each a grid of days and 9 periods. The start and end time of each period is in that table's Time header. "
                + "Classes run Sunday to Thursday. "
                + "I am in Section " + section + ", lab group " + group + ". Extract ONLY Section " + section
                + " classes: all theory classes, plus lab classes marked with my group (" + group + "). "
                + "Skip labs marked with the other group. Ignore the BREAK columns.\n"
                + "Rules:\n"
                + "- A class can span several periods: startTime = start of its first period, endTime = end of its last period.\n"
                + "- title = course code plus title from the 'List of Courses' table, like \"CSE 2201 Algorithm Analysis and Design\".\n"
                + "- room: from the 'Room No.' line under the table. Labs use the lab room listed for that course code. "
                + "Theory classes use the theory room, and a * after a course means the room marked with * in that line.\n"
                + "- details: the teacher initials in brackets, like \"Teacher: SA+EK\".\n"
                + "- Times like 01:10 or 02:30 are afternoon: convert everything to 24-hour (13:10, 14:30).\n"
                + "Return only a JSON array of {\"title\", \"dayOfWeek\" (1=Sunday, 2=Monday, 3=Tuesday, "
                + "4=Wednesday, 5=Thursday, 6=Friday, 7=Saturday), "
                + "\"startTime\" (\"HH:mm\"), \"endTime\" (\"HH:mm\"), \"room\", \"details\"}. "
                + "If nothing is readable, return [].";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        db = AppDatabase.get(this);

        // Adapter
        adapter = new EventAdapter(new EventAdapter.Listener() {
            @Override public void onClick(RoutineEvent e) {
                showAttendanceDialog(e, latestDateFor(e.dayOfWeek));
            }
            @Override public void onLongClick(RoutineEvent e) {
                confirmDelete(e);
            }
        });
        binding.rvEvents.setLayoutManager(new LinearLayoutManager(this));
        binding.rvEvents.setAdapter(adapter);

        // Day tabs, today selected first
        for (String d : SHORT_DAYS) {
            binding.tabDays.addTab(binding.tabDays.newTab().setText(d));
        }
        selectedDay = todayNumber();
        TabLayout.Tab todayTab = binding.tabDays.getTabAt(selectedDay - 1);
        if (todayTab != null) todayTab.select();
        binding.tabDays.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                selectedDay = tab.getPosition() + 1;
                refreshList();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        // Data
        db.routineDao().getAll().observe(this, events -> {
            allEvents = events;
            refreshList();
            ClassScheduler.scheduleAllAsync(this);
        });

        db.attendanceDao().getAll().observe(this, list -> applyAttendance(list));

        // Buttons
        binding.fabAdd.setOnClickListener(v -> showAddDialog());
        binding.btnPayments.setOnClickListener(v -> startActivity(new Intent(this, PaymentsActivity.class)));
        binding.btnAttendance.setOnClickListener(v -> startActivity(new Intent(this, AttendanceActivity.class)));
        binding.btnImport.setOnClickListener(v -> {
            routinePrompt = buildRoutinePrompt(UserProfile.section(this), UserProfile.group(this));
            imagePicker.launch("image/*");
        });
        binding.tvHeader.setOnClickListener(v -> showProfileDialog(true));   // tap the title to edit profile

        requestSmsPermissions();
        if (!UserProfile.isSet(this)) showProfileDialog(false);
        NotificationHelper.createChannel(this);
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // ---------- day helpers ----------

    /** 1 = Sunday ... 7 = Saturday */
    private int todayNumber() {
        return LocalDate.now().getDayOfWeek().getValue() % 7 + 1;
    }

    /** Most recent date (today or earlier) that falls on the given day number. */
    private LocalDate latestDateFor(int dayNum) {
        LocalDate today = LocalDate.now();
        if (dayNum < 1 || dayNum > 7) return today;
        int diff = (todayNumber() - dayNum + 7) % 7;
        return today.minusDays(diff);
    }

    private void refreshList() {
        List<RoutineEvent> filtered = new ArrayList<>();
        for (RoutineEvent e : allEvents) {
            if (e.dayOfWeek == selectedDay) filtered.add(e);
        }
        adapter.setItems(filtered);
        binding.tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
    private String statusFor(RoutineEvent event, LocalDate date) {
        for (Attendance a : attendanceList) {
            if (java.util.Objects.equals(a.courseTitle, event.title)
                    && java.util.Objects.equals(a.date, date.toString())
                    && java.util.Objects.equals(a.startTime, event.startTime)) {
                return a.present ? "Present" : "Absent";
            }
        }
        return "Not recorded";
    }
    // ---------- attendance ----------

    private void showAttendanceDialog(RoutineEvent event, LocalDate date) {
        String label = date.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.US));
        String[] options = {"Present", "Absent", "Remove record", "Change date…"};
        new AlertDialog.Builder(this)
                .setTitle(event.title + "\n" + label + " · " + statusFor(event, date))
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: saveAttendance(event, date, true); break;
                        case 1: saveAttendance(event, date, false); break;
                        case 2: removeAttendance(event, date); break;
                        case 3: pickDate(event, date); break;
                    }
                })
                .show();
    }

    private void pickDate(RoutineEvent event, LocalDate current) {
        new DatePickerDialog(this,
                (view, y, m, d) -> showAttendanceDialog(event, LocalDate.of(y, m + 1, d)),
                current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
    }

    private void saveAttendance(RoutineEvent event, LocalDate date, boolean present) {
        Attendance a = new Attendance();
        a.courseTitle = event.title;
        a.date = date.toString();
        a.startTime = event.startTime;
        a.present = present;
        AppDatabase.dbExecutor.execute(() -> db.attendanceDao().upsert(a));
        String label = date.format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.US));
        Toast.makeText(this, (present ? "Present" : "Absent") + " saved for " + label,
                Toast.LENGTH_SHORT).show();
    }

    private void removeAttendance(RoutineEvent event, LocalDate date) {
        AppDatabase.dbExecutor.execute(() ->
                db.attendanceDao().remove(event.title, date.toString(), event.startTime));
    }

    // ---------- SMS ----------

    private void requestSmsPermissions() {
        boolean granted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                        && ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            SmsImporter.importAll(this);
        } else {
            smsPermLauncher.launch(new String[]{Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS});
        }
    }

    // ---------- add / delete ----------

    private void showAddDialog() {
        DialogAddEventBinding d = DialogAddEventBinding.inflate(getLayoutInflater());
        d.spDay.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, DAYS));
        d.spDay.setSelection(selectedDay - 1);

        new AlertDialog.Builder(this)
                .setTitle("Add to routine")
                .setView(d.getRoot())
                .setPositiveButton("Save", (dialog, which) -> {
                    String title = d.etTitle.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    RoutineEvent e = new RoutineEvent();
                    e.title = title;
                    e.type = "CLASS";
                    e.dayOfWeek = d.spDay.getSelectedItemPosition() + 1;   // Sunday = 1
                    e.startTime = d.etStart.getText().toString().trim();
                    e.endTime = d.etEnd.getText().toString().trim();
                    e.room = d.etRoom.getText().toString().trim();
                    e.source = "MANUAL";
                    e.createdAt = System.currentTimeMillis();
                    AppDatabase.dbExecutor.execute(() -> db.routineDao().insert(e));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(RoutineEvent event) {
        new AlertDialog.Builder(this)
                .setTitle("Delete \"" + event.title + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ClassScheduler.cancel(this, event.id);
                    AppDatabase.dbExecutor.execute(() -> db.routineDao().delete(event));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- photo import ----------

    private Bitmap loadScaled(Uri uri) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 3000) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, o);
        }
    }

    private void importRoutineFrom(Uri uri) {
        Bitmap bmp;
        try {
            bmp = loadScaled(uri);
        } catch (IOException e) {
            bmp = null;
        }
        if (bmp == null) {
            Toast.makeText(this, "Couldn't open that image", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog loading = new AlertDialog.Builder(this)
                .setMessage("Reading your routine…").setCancelable(false).show();
        AiClient.ask(routinePrompt, bmp, new AiClient.Callback() {
            @Override public void onSuccess(String text) {
                loading.dismiss();
                showReview(text);
            }
            @Override public void onError(String message) {
                loading.dismiss();
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showReview(String json) {
        List<RoutineEvent> parsed = RoutineParser.tryParse(json, "IMAGE");
        if (parsed == null) {
            Toast.makeText(this, "Couldn't understand the AI reply", Toast.LENGTH_LONG).show();
            return;
        }
        if (parsed.isEmpty()) {
            Toast.makeText(this, "No classes found in that photo", Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[parsed.size()];
        boolean[] checked = new boolean[parsed.size()];
        for (int i = 0; i < parsed.size(); i++) {
            RoutineEvent e = parsed.get(i);
            String day = (e.dayOfWeek >= 1 && e.dayOfWeek <= 7) ? DAYS[e.dayOfWeek - 1] : "?";
            labels[i] = day + " " + e.startTime + "-" + e.endTime + "  " + e.title;
            checked[i] = true;
        }
        new AlertDialog.Builder(this)
                .setTitle("Add these classes?")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Add selected", (d, w) ->
                        AppDatabase.dbExecutor.execute(() -> {
                            for (int i = 0; i < parsed.size(); i++) {
                                if (checked[i]) db.routineDao().insert(parsed.get(i));
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- profile ----------

    private void showProfileDialog(boolean editing) {
        DialogProfileBinding d = DialogProfileBinding.inflate(getLayoutInflater());
        String[] groups = {"A1", "A2", "B1", "B2"};
        d.spGroup.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, groups));
        if (editing) {
            d.etRoll.setText(UserProfile.roll(this));
            d.etPhone.setText(UserProfile.phone(this));
            int idx = Arrays.asList(groups).indexOf(UserProfile.group(this));
            if (idx >= 0) d.spGroup.setSelection(idx);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "Edit profile" : "Welcome to Fluxora")
                .setView(d.getRoot())
                .setCancelable(editing)
                .setPositiveButton("Save", null)
                .setNegativeButton(editing ? "Cancel" : null, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String roll = d.etRoll.getText().toString().trim();
            if (roll.isEmpty()) {
                d.etRoll.setError("Required");
                return;
            }
            UserProfile.save(this, roll, groups[d.spGroup.getSelectedItemPosition()],
                    d.etPhone.getText().toString().trim());
            dialog.dismiss();
        });
    }
    private void applyAttendance(List<Attendance> list) {
        attendanceList = list;
        Map<String, int[]> stats = new HashMap<>();
        for (Attendance a : list) {
            android.util.Log.d("Fluxora", "row: " + a.courseTitle + " | " + a.date
                    + " | " + a.startTime + " | present=" + a.present);
            int[] v = stats.computeIfAbsent(a.courseTitle, k -> new int[2]);
            if (a.present) v[0]++;
            v[1]++;
        }
        adapter.setStats(stats);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppDatabase.dbExecutor.execute(() -> {
            List<Attendance> list = db.attendanceDao().getAllNow();
            runOnUiThread(() -> applyAttendance(list));
        });
    }
}