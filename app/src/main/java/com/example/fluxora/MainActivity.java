package com.example.fluxora;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.RoutineEvent;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AppDatabase db;
    private EventAdapter adapter;

    private static final String[] DAYS =
            {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private final ActivityResultLauncher<String[]> smsPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.READ_SMS))) {
                    SmsImporter.importAll(this);
                }
            });
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

        adapter = new EventAdapter(this::confirmDelete);
        binding.rvEvents.setLayoutManager(new LinearLayoutManager(this));
        binding.rvEvents.setAdapter(adapter);

        db.routineDao().getAll().observe(this, events -> adapter.setItems(events));

        binding.fabAdd.setOnClickListener(v -> showAddDialog());
        binding.btnPayments.setOnClickListener(v -> startActivity(new Intent(this, PaymentsActivity.class)));
        requestSmsPermissions();
    }
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
    private void showAddDialog() {
        DialogAddEventBinding d = DialogAddEventBinding.inflate(getLayoutInflater());
        d.spDay.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, DAYS));

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
                .setPositiveButton("Delete", (dialog, which) ->
                        AppDatabase.dbExecutor.execute(() -> db.routineDao().delete(event)))
                .setNegativeButton("Cancel", null)
                .show();
    }
}