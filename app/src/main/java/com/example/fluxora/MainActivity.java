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
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AppDatabase db;
    private EventAdapter adapter;
    private String routinePrompt;
    private List<RoutineEvent> allEvents = new ArrayList<>();
    private List<Attendance> attendanceList = new ArrayList<>();
    private int selectedDay;   // 1 = Sunday ... 7 = Saturday, 8 = Upcoming

    private static final int UPCOMING_TAB = 8;

    // Week starts on Sunday: dayOfWeek 1 = Sunday ... 7 = Saturday
    private static final String[] DAYS =
            {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    private static final String[] SHORT_DAYS =
            {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

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

    // ---------- prompts ----------

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

    private String buildChatPrompt(String message) {
        LocalDate today = LocalDate.now();
        String todayStr = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US) + " " + today;

        Map<String, List<String>> byTitle = new LinkedHashMap<>();
        for (RoutineEvent e : allEvents) {
            if (e.dayOfWeek >= 1 && e.dayOfWeek <= 7) {
                byTitle.computeIfAbsent(e.title, k -> new ArrayList<>())
                        .add(SHORT_DAYS[e.dayOfWeek - 1] + " " + e.startTime);
            }
        }
        StringBuilder courses = new StringBuilder();
        for (Map.Entry<String, List<String>> en : byTitle.entrySet()) {
            courses.append("- ").append(en.getKey())
                    .append(" (").append(String.join(", ", en.getValue())).append(")\n");
        }
        if (courses.length() == 0) courses.append("(no classes saved yet)\n");

        return "You turn a student's short message into items for their class routine.\n"
                + "Today is " + todayStr + ". The week starts on Sunday and classes run Sunday to Thursday.\n"
                + "The student's regular classes (use these exact titles when the message refers to one):\n"
                + courses
                + "Message: \"" + message.replace("\"", "'") + "\"\n"
                + "Return ONLY a JSON array (an empty array if there is nothing to add). Each item:\n"
                + "{\"type\": one of EXTRA_CLASS, CANCELLED, ASSIGNMENT, DEADLINE, OTHER, "
                + "\"title\": the exact course title from the list when the message refers to one, otherwise a short title, "
                + "\"date\": \"yyyy-MM-dd\" (work out words like tomorrow, Sunday or next Thursday from today's date), "
                + "\"startTime\": \"HH:mm\" in 24-hour time, or \"\" if not mentioned, "
                + "\"endTime\": \"HH:mm\" or \"\", \"room\": \"\" if not mentioned, "
                + "\"details\": short extra info such as assignment topic, marks or what to submit}\n"
                + "Rules:\n"
                + "- A cancelled regular class is type CANCELLED, with the class's usual startTime from the list.\n"
                + "- If a regular class is moved, return two items: CANCELLED for the original date and EXTRA_CLASS for the new date and time.\n"
                + "- ASSIGNMENT: date is the due date. DEADLINE: any other deadline. OTHER: quizzes, tests and notes.\n"
                + "- Every item needs a date. Do not invent details that were not in the message.";
    }

    // ---------- lifecycle ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        db = AppDatabase.get(this);

        // Adapter
        adapter = new EventAdapter(new EventAdapter.Listener() {
            @Override public void onClick(RoutineEvent e) {
                if (e.dayOfWeek >= 1 && e.dayOfWeek <= 7) {
                    showAttendanceDialog(e, latestDateFor(e.dayOfWeek));
                } else if ("EXTRA_CLASS".equals(e.type) && e.date != null) {
                    showAttendanceDialog(e, LocalDate.parse(e.date));
                } else {
                    showInfo(e);
                }
            }
            @Override public void onLongClick(RoutineEvent e) {
                confirmDelete(e);
            }
        });
        binding.rvEvents.setLayoutManager(new LinearLayoutManager(this));
        binding.rvEvents.setAdapter(adapter);

        // Day tabs (Sun..Sat + Upcoming), today selected first
        for (String d : SHORT_DAYS) {
            binding.tabDays.addTab(binding.tabDays.newTab().setText(d));
        }
        binding.tabDays.addTab(binding.tabDays.newTab().setText("Upcoming"));
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
        binding.btnWhatsApp.setOnClickListener(v -> showWhatsAppDialog());
        binding.fabAdd.setOnClickListener(v -> showAddDialog());
        binding.btnSend.setOnClickListener(v -> sendChat());
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

    @Override
    protected void onResume() {
        super.onResume();
        AppDatabase.dbExecutor.execute(() -> {
            List<Attendance> list = db.attendanceDao().getAllNow();
            runOnUiThread(() -> applyAttendance(list));
        });
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private void refreshList() {
        List<RoutineEvent> filtered = new ArrayList<>();
        if (selectedDay == UPCOMING_TAB) {
            String today = LocalDate.now().toString();
            for (RoutineEvent e : allEvents) {
                if (e.dayOfWeek == 0 && e.date != null && e.date.compareTo(today) >= 0) filtered.add(e);
            }
            filtered.sort((a, b) ->
                    (a.date + " " + nz(a.startTime)).compareTo(b.date + " " + nz(b.startTime)));
            binding.tvEmpty.setText("Nothing upcoming");
        } else {
            for (RoutineEvent e : allEvents) {
                if (e.dayOfWeek == selectedDay) filtered.add(e);
            }
            binding.tvEmpty.setText("No classes on this day");
        }
        adapter.setItems(filtered);
        binding.tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---------- attendance ----------

    private void applyAttendance(List<Attendance> list) {
        attendanceList = list;
        Map<String, int[]> stats = new HashMap<>();
        for (Attendance a : list) {
            int[] v = stats.computeIfAbsent(a.courseTitle, k -> new int[2]);
            if (a.present) v[0]++;
            v[1]++;
        }
        adapter.setStats(stats);
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

    // ---------- chat box ----------

    private void sendChat() {
        String msg = binding.etChat.getText().toString().trim();
        if (msg.isEmpty()) return;
        binding.btnSend.setEnabled(false);
        AiClient.ask(PromptBuilder.update(this, msg, allEvents, false), null, new AiClient.Callback() {
            @Override public void onSuccess(String text) {
                binding.btnSend.setEnabled(true);
                if (reviewFromJson(text, "CHAT")) binding.etChat.setText("");
            }
            @Override public void onError(String message) {
                binding.btnSend.setEnabled(true);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showInfo(RoutineEvent e) {
        StringBuilder sb = new StringBuilder(EventAdapter.typeLabel(e.type));
        if (e.date != null) sb.append("\nDate: ").append(e.date);
        if (!nz(e.startTime).isEmpty()) sb.append("\nTime: ").append(e.startTime);
        if (!nz(e.endTime).isEmpty()) sb.append(" - ").append(e.endTime);
        if (!nz(e.room).isEmpty()) sb.append("\nRoom: ").append(e.room);
        if (!nz(e.details).isEmpty()) sb.append("\n").append(e.details);
        new AlertDialog.Builder(this)
                .setTitle(e.title)
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
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
        d.spDay.setSelection((selectedDay <= 7 ? selectedDay : todayNumber()) - 1);

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
                reviewFromJson(text, "IMAGE");
            }
            @Override public void onError(String message) {
                loading.dismiss();
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Parses the AI reply and shows the review dialog. Returns true if there was something to review. */
    private boolean reviewFromJson(String json, String source) {
        List<RoutineEvent> parsed = RoutineParser.tryParse(json, source);
        if (parsed == null) {
            Toast.makeText(this, "Couldn't understand the AI reply", Toast.LENGTH_LONG).show();
            return false;
        }
        if (parsed.isEmpty()) {
            Toast.makeText(this, source.equals("CHAT")
                    ? "Nothing to add from that message"
                    : "No classes found in that photo", Toast.LENGTH_LONG).show();
            return false;
        }
        showReview(parsed);
        return true;
    }

    private void showReview(List<RoutineEvent> parsed) {
        String[] labels = new String[parsed.size()];
        boolean[] checked = new boolean[parsed.size()];
        boolean anyDated = false;
        for (int i = 0; i < parsed.size(); i++) {
            RoutineEvent e = parsed.get(i);
            if (e.dayOfWeek >= 1 && e.dayOfWeek <= 7) {
                labels[i] = DAYS[e.dayOfWeek - 1] + " " + e.startTime + "-" + e.endTime + "  " + e.title;
            } else {
                anyDated = true;
                labels[i] = EventAdapter.typeLabel(e.type) + " · " + e.date
                        + (nz(e.startTime).isEmpty() ? "" : " " + e.startTime)
                        + "\n" + e.title
                        + (nz(e.details).isEmpty() ? "" : "\n" + e.details);
            }
            checked[i] = true;
        }
        final boolean showUpcoming = anyDated;
        new AlertDialog.Builder(this)
                .setTitle("Add these?")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Add selected", (d, w) ->
                        AppDatabase.dbExecutor.execute(() -> {
                            for (int i = 0; i < parsed.size(); i++) {
                                if (checked[i]) db.routineDao().insert(parsed.get(i));
                            }
                            if (showUpcoming) {
                                runOnUiThread(() -> {
                                    TabLayout.Tab t = binding.tabDays.getTabAt(UPCOMING_TAB - 1);
                                    if (t != null) t.select();
                                });
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
    private boolean hasListenerAccess() {
        return androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(this).contains(getPackageName());
    }

    private void showWhatsAppDialog() {
        if (!hasListenerAccess()) {
            new AlertDialog.Builder(this)
                    .setTitle("Allow notification access")
                    .setMessage("Fluxora reads WhatsApp notifications to find class updates. "
                            + "Only the groups you choose are used.\n\nTurn on Fluxora in the next screen.")
                    .setPositiveButton("Open settings", (d, w) -> startActivity(new Intent(
                            android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        java.util.Set<String> selected = WhatsAppPrefs.selected(this);
        List<String> chats = WhatsAppPrefs.seenChats(this);
        for (String s : selected) {
            if (!chats.contains(s)) chats.add(s);
        }
        if (chats.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("WhatsApp class groups")
                    .setMessage("No chats seen yet. When someone posts in your class group "
                            + "(notifications must be on for it), it shows up here. Then come back and tick it.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] names = chats.toArray(new String[0]);
        boolean[] checked = new boolean[names.length];
        for (int i = 0; i < names.length; i++) checked[i] = selected.contains(names[i]);

        boolean on = WhatsAppPrefs.enabled(this);
        new AlertDialog.Builder(this)
                .setTitle(on ? "Which chats are your class groups?" : "Paused. Pick your class groups")
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Save", (d, w) -> {
                    java.util.Set<String> out = new java.util.HashSet<>();
                    for (int i = 0; i < names.length; i++) {
                        if (checked[i]) out.add(names[i]);
                    }
                    WhatsAppPrefs.setSelected(this, out);
                })
                .setNeutralButton(on ? "Pause" : "Resume", (d, w) -> WhatsAppPrefs.setEnabled(this, !on))
                .setNegativeButton("Cancel", null)
                .show();
    }
}