package com.example.fluxora;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.fluxora.databinding.ActivitySettingsBinding;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AiPrefs.init(this);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.swAi.setChecked(AiPrefs.enabled());
        binding.swAi.setOnCheckedChangeListener((button, on) -> AiPrefs.setEnabled(on));

        binding.etKey.setText(AiPrefs.userKey());
        binding.btnSaveKey.setOnClickListener(v -> saveKey());
        binding.btnClearKey.setOnClickListener(v -> {
            AiPrefs.setUserKey("");
            binding.etKey.setText("");
            updateStatus();
            Toast.makeText(this, "Removed. Using the built-in key.", Toast.LENGTH_SHORT).show();
        });
        binding.btnTestKey.setOnClickListener(v -> testKey());
        updateStatus();
    }

    private void updateStatus() {
        binding.tvKeyStatus.setText(AiPrefs.usingOwnKey()
                ? "Using: your own key"
                : "Using: the app's built-in key");
    }

    /** Removes spaces and quotes that often come along when a key is pasted. */
    private String typedKey() {
        return binding.etKey.getText().toString().replaceAll("[\\s\"']", "");
    }

    private void saveKey() {
        AiPrefs.setUserKey(typedKey());
        updateStatus();
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }

    private void testKey() {
        String typed = typedKey();
        String key = typed.isEmpty() ? AiPrefs.effectiveKey() : typed;
        if (key == null || key.isEmpty()) {
            binding.tvTestResult.setText("No key to test.");
            return;
        }
        binding.btnTestKey.setEnabled(false);
        binding.tvTestResult.setText("Testing…");
        AiClient.askWithKey(key, "Reply with only the JSON array []", null, new AiClient.Callback() {
            @Override public void onSuccess(String text) {
                binding.tvTestResult.setText("Key works ✓");
                binding.btnTestKey.setEnabled(true);
            }
            @Override public void onError(String message) {
                binding.tvTestResult.setText("Test failed: " + message);
                binding.btnTestKey.setEnabled(true);
            }
        });
    }
}