package com.example.fluxora;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.fluxora.databinding.ActivityMainBinding;
import com.example.fluxora.databinding.DialogAddEventBinding;
import com.example.fluxora.databinding.DialogProfileBinding;
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.Attendance;
import com.example.fluxora.db.RoutineEvent;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AppDatabase db;
    private EventAdapter adapter;
    private CalendarAdapter calendarAdapter;
    private PaymentAdapter paymentAdapter;

    private DatabaseReference noticeRef;
    private ValueEventListener noticeListener;

    private String routinePrompt;
    private List<RoutineEvent> allEvents = new ArrayList<>();
    private List<Attendance> attendanceList = new ArrayList<>();
    private YearMonth month = YearMonth.now();

    private int selectedDay;   // 1 = Sunday ... 7 = Saturday, 8 = Upcoming
    private int currentTab = 0; // 0 = Schedule, 1 = Calendar, 2 = Ledger

    private static final int UPCOMING_TAB = 8;

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

    // ---------- lifecycle ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AiPrefs.init(this);
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

        // --- PAGE 0: SCHEDULE SETUP ---
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

        // --- PAGE 1: CALENDAR SETUP ---
        calendarAdapter = new CalendarAdapter(this::showCalendarDay);
        binding.rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        binding.rvCalendar.setAdapter(calendarAdapter);

        binding.btnPrev.setOnClickListener(v -> {
            month = month.minusMonths(1);
            refreshCalendar();
        });
        binding.btnNext.setOnClickListener(v -> {
            month = month.plusMonths(1);
            refreshCalendar();
        });

        // --- PAGE 2: LEDGER / PAYMENTS SETUP ---
        paymentAdapter = new PaymentAdapter();
        binding.rvPayments.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPayments.setAdapter(paymentAdapter);
        db.paymentDao().getAll().observe(this, paymentAdapter::setItems);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        db.paymentDao().totalSpentSince(cal.getTimeInMillis()).observe(this, total ->
                binding.tvTotal.setText("Spent this month: Tk "
                        + String.format(Locale.US, "%.2f", total == null ? 0.0 : total)));

        // --- DATABASE OBSERVERS ---
        db.routineDao().getAll().observe(this, events -> {
            allEvents = events;
            refreshList();
            ClassScheduler.scheduleAllAsync(this);
        });
        db.attendanceDao().getAll().observe(this, list -> applyAttendance(list));

        // --- TOP BAR & ACTION BUTTONS ---
        binding.btnWhatsApp.setOnClickListener(v -> showWhatsAppDialog());
        binding.fabAdd.setOnClickListener(v -> showAddDialog());
        binding.btnSend.setOnClickListener(v -> sendChat());
        binding.btnAccount.setOnClickListener(v -> startActivity(new Intent(this, AccountActivity.class)));
        binding.btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        binding.btnImport.setOnClickListener(v -> {
            if (!NetworkUtils.requireInternet(this)) return;
            routinePrompt = buildRoutinePrompt(UserProfile.section(this), UserProfile.group(this));
            imagePicker.launch("image/*");
        });
        binding.btnBubble.setOnClickListener(v -> toggleQuickBubble());
        binding.btnPostNotice.setOnClickListener(v -> showPostNoticeDialog());
        binding.tvHeader.setOnClickListener(v -> showProfileDialog(true));

        // --- FIXED BOTTOM DOCK NAVIGATION ---
        binding.btnSchedule.setOnClickListener(v -> switchTab(0));
        binding.btnAttendance.setOnClickListener(v -> switchTab(1));
        binding.btnPayments.setOnClickListener(v -> switchTab(2));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentTab != 0) {
                    switchTab(0);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        refreshCalendar();
        subscribeToSectionNoticeBoard();
        requestSmsPermissions();
        if (!UserProfile.isSet(this)) showProfileDialog(false);
        NotificationHelper.createChannel(this);
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (getIntent() != null && getIntent().getBooleanExtra("quick_add", false)) {
            showAddDialog();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("quick_add", false)) {
            switchTab(0);
            showAddDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppDatabase.dbExecutor.execute(() -> {
            List<Attendance> list = db.attendanceDao().getAllNow();
            runOnUiThread(() -> applyAttendance(list));
        });
        int ai = AiPrefs.enabled() ? View.VISIBLE : View.GONE;
        binding.chatBar.setVisibility(ai);
        binding.btnImport.setVisibility(ai);
        binding.btnBubble.setText(QuickBubbleService.isRunning ? "Stop Bubble" : "+ Quick Bubble");
    }

    // ---------- Foreground Service: Quick-Add Bubble ----------

    private void toggleQuickBubble() {
        if (QuickBubbleService.isRunning) {
            stopService(new Intent(this, QuickBubbleService.class));
            binding.btnBubble.setText("+ Quick Bubble");
            Toast.makeText(this, "Quick-add bubble stopped", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant 'Display over other apps' permission for the bubble", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent serviceIntent = new Intent(this, QuickBubbleService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        binding.btnBubble.setText("Stop Bubble");
        Toast.makeText(this, "Floating quick-add bubble active", Toast.LENGTH_SHORT).show();
    }

    // ---------- Live Section Notice Board (Firebase Realtime Database) ----------

    private void subscribeToSectionNoticeBoard() {
        String sec = UserProfile.section(this);
        if (sec == null || sec.isEmpty()) sec = "A";
        binding.tvNoticeHeader.setText("LIVE SECTION " + sec + " NOTICE BOARD");

        if (noticeRef != null && noticeListener != null) {
            noticeRef.removeEventListener(noticeListener);
        }

        noticeRef = FirebaseDatabase.getInstance().getReference("section_notices").child("SEC_" + sec);
        noticeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String text = snapshot.child("text").getValue(String.class);
                String author = snapshot.child("author").getValue(String.class);
                if (text != null && !text.trim().isEmpty()) {
                    String prefix = (author != null && !author.isEmpty()) ? ("[" + author + "] ") : "";
                    binding.tvNoticeBoard.setText(prefix + text);
                } else {
                    binding.tvNoticeBoard.setText("No live notices for Section " + UserProfile.section(MainActivity.this) + " yet.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        noticeRef.addValueEventListener(noticeListener);
    }

    private void showPostNoticeDialog() {
        if (!NetworkUtils.requireInternet(this)) return;

        String sec = UserProfile.section(this);
        EditText input = new EditText(this);
        input.setHint("e.g. Tomorrow's CSE 2201 CT moved to 11:00 AM");
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Post to Section " + sec + " Board")
                .setView(input)
                .setPositiveButton("Post Live", (d, w) -> {
                    if (!NetworkUtils.requireInternet(this)) return;
                    String msg = input.getText().toString().trim();
                    if (msg.isEmpty()) return;
                    Map<String, Object> data = new HashMap<>();
                    data.put("text", msg);
                    data.put("author", "Roll " + UserProfile.roll(this));
                    data.put("timestamp", System.currentTimeMillis());
                    FirebaseDatabase.getInstance()
                            .getReference("section_notices")
                            .child("SEC_" + sec)
                            .setValue(data)
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(this, "Notice posted live!", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Could not post: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNeutralButton("Clear Board", (d, w) -> {
                    if (!NetworkUtils.requireInternet(this)) return;
                    FirebaseDatabase.getInstance()
                            .getReference("section_notices")
                            .child("SEC_" + sec)
                            .removeValue();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- bottom dock tab switching & directional animation ----------

    private void switchTab(int targetTab) {
        if (targetTab == currentTab) return;

        if (targetTab > currentTab) {
            binding.viewFlipper.setInAnimation(this, R.anim.slide_in_right);
            binding.viewFlipper.setOutAnimation(this, R.anim.slide_out_left);
        } else {
            binding.viewFlipper.setInAnimation(this, R.anim.slide_in_left);
            binding.viewFlipper.setOutAnimation(this, R.anim.slide_out_right);
        }

        binding.viewFlipper.setDisplayedChild(targetTab);
        currentTab = targetTab;

        if (targetTab == 0) binding.fabAdd.show();
        else binding.fabAdd.hide();

        styleDockButton(binding.btnSchedule, targetTab == 0);
        styleDockButton(binding.btnAttendance, targetTab == 1);
        styleDockButton(binding.btnPayments, targetTab == 2);
    }

    private void styleDockButton(Button button, boolean active) {
        TypedValue tv = new TypedValue();
        if (active) {
            button.setBackgroundResource(R.drawable.bg_tab_pill_selector);
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, tv, true);
            button.setTextColor(tv.data);
            button.setTypeface(null, Typeface.BOLD);
            if (button instanceof MaterialButton) {
                ((MaterialButton) button).setBackgroundTintList(null);
                ((MaterialButton) button).setIconTint(ColorStateList.valueOf(tv.data));
            }
        } else {
            button.setBackgroundColor(Color.TRANSPARENT);
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true);
            button.setTextColor(tv.data);
            button.setTypeface(null, Typeface.NORMAL);
            if (button instanceof MaterialButton) {
                ((MaterialButton) button).setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true);
                ((MaterialButton) button).setIconTint(ColorStateList.valueOf(tv.data));
            }
        }
    }

    // ---------- day helpers ----------

    private int todayNumber() {
        return LocalDate.now().getDayOfWeek().getValue() % 7 + 1;
    }

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

    // ---------- attendance & calendar ----------

    private void applyAttendance(List<Attendance> list) {
        attendanceList = list;
        Map<String, int[]> stats = new HashMap<>();
        for (Attendance a : list) {
            int[] v = stats.computeIfAbsent(a.courseTitle, k -> new int[2]);
            if (a.present) v[0]++;
            v[1]++;
        }
        adapter.setStats(stats);
        refreshCalendar();
    }

    private void refreshCalendar() {
        if (calendarAdapter == null) return;
        binding.tvMonth.setText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)));

        List<LocalDate> days = new ArrayList<>();
        int offset = month.atDay(1).getDayOfWeek().getValue() % 7;
        for (int i = 0; i < offset; i++) days.add(null);
        for (int d = 1; d <= month.lengthOfMonth(); d++) days.add(month.atDay(d));

        Map<String, int[]> summary = new HashMap<>();
        for (Attendance a : attendanceList) {
            int[] v = summary.computeIfAbsent(a.date, k -> new int[2]);
            if (a.present) v[0]++; else v[1]++;
        }
        calendarAdapter.setData(days, summary);
    }

    private void showCalendarDay(LocalDate date) {
        List<Attendance> records = new ArrayList<>();
        for (Attendance a : attendanceList) {
            if (date.toString().equals(a.date)) records.add(a);
        }
        String label = date.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.US));
        if (records.isEmpty()) {
            Toast.makeText(this, "Nothing recorded on " + label, Toast.LENGTH_SHORT).show();
            return;
        }
        records.sort((x, y) -> String.valueOf(x.startTime).compareTo(String.valueOf(y.startTime)));

        String[] items = new String[records.size()];
        for (int i = 0; i < records.size(); i++) {
            Attendance a = records.get(i);
            items[i] = (a.present ? "✓ " : "✗ ") + a.courseTitle + "  (" + a.startTime + ")";
        }
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(items, (dialog, which) -> editCalendarRecord(records.get(which)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void editCalendarRecord(Attendance a) {
        String[] options = {"Mark present", "Mark absent", "Delete this record"};
        new AlertDialog.Builder(this)
                .setTitle(a.courseTitle)
                .setItems(options, (dialog, which) -> {
                    if (which == 2) {
                        AppDatabase.dbExecutor.execute(() ->
                                db.attendanceDao().remove(a.courseTitle, a.date, a.startTime));
                    } else {
                        Attendance copy = new Attendance();
                        copy.courseTitle = a.courseTitle;
                        copy.date = a.date;
                        copy.startTime = a.startTime;
                        copy.present = (which == 0);
                        AppDatabase.dbExecutor.execute(() -> db.attendanceDao().upsert(copy));
                    }
                })
                .show();
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

    // ---------- chat box (with offline check) ----------

    private void sendChat() {
        String msg = binding.etChat.getText().toString().trim();
        if (msg.isEmpty()) return;
        if (!NetworkUtils.requireInternet(this)) return;

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
                    e.dayOfWeek = d.spDay.getSelectedItemPosition() + 1;
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

    // ---------- photo import (with offline check) ----------

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
        if (!NetworkUtils.requireInternet(this)) return;

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
            subscribeToSectionNoticeBoard(); // Re-bind live notice board if section changed
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