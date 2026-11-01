package com.example.fluxora;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import com.example.fluxora.databinding.ActivityAttendanceBinding;
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.Attendance;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceActivity extends AppCompatActivity {

    private ActivityAttendanceBinding binding;
    private AppDatabase db;
    private CalendarAdapter adapter;
    private YearMonth month = YearMonth.now();
    private List<Attendance> all = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAttendanceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.get(this);

        adapter = new CalendarAdapter(this::showDay);
        binding.rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        binding.rvCalendar.setAdapter(adapter);

        binding.btnPrev.setOnClickListener(v -> {
            month = month.minusMonths(1);
            refresh();
        });
        binding.btnNext.setOnClickListener(v -> {
            month = month.plusMonths(1);
            refresh();
        });

        db.attendanceDao().getAll().observe(this, list -> {
            all = list;
            refresh();
        });
        refresh();
    }

    private void refresh() {
        binding.tvMonth.setText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)));

        List<LocalDate> days = new ArrayList<>();
        int offset = month.atDay(1).getDayOfWeek().getValue() % 7;   // Sunday = 0 blanks
        for (int i = 0; i < offset; i++) days.add(null);
        for (int d = 1; d <= month.lengthOfMonth(); d++) days.add(month.atDay(d));

        Map<String, int[]> summary = new HashMap<>();
        for (Attendance a : all) {
            int[] v = summary.computeIfAbsent(a.date, k -> new int[2]);
            if (a.present) v[0]++; else v[1]++;
        }
        adapter.setData(days, summary);
    }

    private void showDay(LocalDate date) {
        List<Attendance> records = new ArrayList<>();
        for (Attendance a : all) {
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
                .setItems(items, (dialog, which) -> editRecord(records.get(which)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void editRecord(Attendance a) {
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
}