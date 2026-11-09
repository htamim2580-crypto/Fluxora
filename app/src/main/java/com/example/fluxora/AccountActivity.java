package com.example.fluxora;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.fluxora.databinding.ActivityAccountBinding;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.Arrays;

public class AccountActivity extends AppCompatActivity {

    private ActivityAccountBinding binding;
    private FirebaseAuth auth;
    private static final String[] GROUPS = {"A1", "A2", "B1", "B2"};

    private final ActivityResultLauncher<String> photoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Bitmap circle = ProfileAvatarHelper.saveCircularAvatar(this, uri);
                    if (circle != null) {
                        binding.ivProfilePic.setPadding(0, 0, 0, 0);
                        binding.ivProfilePic.setImageTintList(null);
                        binding.ivProfilePic.setImageBitmap(circle);
                        Toast.makeText(this, "Profile picture updated ✓", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        // Populate Editable Profile Fields
        binding.spProfileGroup.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, GROUPS));
        loadProfileFields();

        binding.avatarContainer.setOnClickListener(v -> photoPicker.launch("image/*"));
        binding.btnChangePhoto.setOnClickListener(v -> photoPicker.launch("image/*"));
        binding.btnSaveProfile.setOnClickListener(v -> saveProfileFields());

        // Google Auth & Cloud Backup Listeners
        binding.btnSignIn.setOnClickListener(v -> signIn());
        binding.btnSignOut.setOnClickListener(v -> signOut());
        binding.btnBackup.setOnClickListener(v -> backup());
        binding.btnRestore.setOnClickListener(v -> confirmRestore());
        updateUi();
    }

    private void loadProfileFields() {
        Bitmap avatar = ProfileAvatarHelper.loadCircularAvatar(this);
        if (avatar != null) {
            binding.ivProfilePic.setPadding(0, 0, 0, 0);
            binding.ivProfilePic.setImageTintList(null);
            binding.ivProfilePic.setImageBitmap(avatar);
        }

        String savedName = ProfileAvatarHelper.getName(this);
        if (savedName.isEmpty() && auth.getCurrentUser() != null && auth.getCurrentUser().getDisplayName() != null) {
            savedName = auth.getCurrentUser().getDisplayName();
        }
        binding.etProfileName.setText(savedName);
        binding.etProfileRoll.setText(UserProfile.roll(this));
        binding.etProfilePhone.setText(UserProfile.phone(this));

        int idx = Arrays.asList(GROUPS).indexOf(UserProfile.group(this));
        if (idx >= 0) binding.spProfileGroup.setSelection(idx);
    }

    private void saveProfileFields() {
        String name = binding.etProfileName.getText().toString().trim();
        String roll = binding.etProfileRoll.getText().toString().trim();
        String phone = binding.etProfilePhone.getText().toString().trim();
        String group = GROUPS[binding.spProfileGroup.getSelectedItemPosition()];

        if (roll.isEmpty()) {
            binding.etProfileRoll.setError("Roll number is required");
            return;
        }

        ProfileAvatarHelper.setName(this, name);
        UserProfile.save(this, roll, group, phone);
        Toast.makeText(this, "Profile saved ✓", Toast.LENGTH_SHORT).show();
    }

    private void updateUi() {
        FirebaseUser user = auth.getCurrentUser();
        boolean signed = user != null;
        if (!signed) {
            binding.tvStatus.setText("Not signed in. Sign in to back up your routine and attendance.");
        } else {
            String name = user.getDisplayName() == null ? "" : user.getDisplayName() + "\n";
            binding.tvStatus.setText("Signed in as\n" + name + user.getEmail());
            if (binding.etProfileName.getText().toString().trim().isEmpty() && user.getDisplayName() != null) {
                binding.etProfileName.setText(user.getDisplayName());
                ProfileAvatarHelper.setName(this, user.getDisplayName());
            }
        }
        binding.btnSignIn.setEnabled(!signed);
        binding.btnSignOut.setEnabled(signed);
        binding.btnBackup.setEnabled(signed);
        binding.btnRestore.setEnabled(signed);
    }

    private void setBusy(boolean busy) {
        if (busy) {
            binding.btnBackup.setEnabled(false);
            binding.btnRestore.setEnabled(false);
        } else {
            updateUi();
        }
    }

    // ---------- backup / restore ----------

    private void backup() {
        if (!NetworkUtils.requireInternet(this)) return;

        setBusy(true);
        binding.tvResult.setText("Backing up…");
        CloudBackup.backup(this, new CloudBackup.Callback() {
            @Override public void onDone(String message) {
                SettingsSync.push(AccountActivity.this, new SettingsSync.Callback() {
                    @Override public void onDone() {
                        binding.tvResult.setText(message + "\nProfile and WhatsApp settings saved");
                        setBusy(false);
                    }
                    @Override public void onError(String m) {
                        binding.tvResult.setText(message + "\nSettings backup failed: " + m);
                        setBusy(false);
                    }
                });
            }
            @Override public void onError(String message) {
                binding.tvResult.setText("Backup failed: " + message);
                setBusy(false);
            }
        });
    }

    private void restore() {
        if (!NetworkUtils.requireInternet(this)) return;

        setBusy(true);
        binding.tvResult.setText("Restoring…");
        CloudBackup.restore(this, new CloudBackup.Callback() {
            @Override public void onDone(String message) {
                SettingsSync.pull(AccountActivity.this, new SettingsSync.Callback() {
                    @Override public void onDone() {
                        loadProfileFields();
                        binding.tvResult.setText(message + "\nProfile and WhatsApp settings restored");
                        setBusy(false);
                    }
                    @Override public void onError(String m) {
                        binding.tvResult.setText(message + "\nSettings restore failed: " + m);
                        setBusy(false);
                    }
                });
            }
            @Override public void onError(String message) {
                binding.tvResult.setText("Restore failed: " + message);
                setBusy(false);
            }
        });
    }

    private void confirmRestore() {
        if (!NetworkUtils.requireInternet(this)) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore from backup?")
                .setMessage("This replaces the routine and attendance on this phone with your last backup.")
                .setPositiveButton("Restore", (d, w) -> restore())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- sign in / out ----------

    private void signIn() {
        if (!NetworkUtils.requireInternet(this)) return;

        GetSignInWithGoogleOption signInOption = new GetSignInWithGoogleOption.Builder(
                getString(R.string.default_web_client_id)).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build();

        CredentialManager.create(this).getCredentialAsync(
                this, request, new CancellationSignal(), ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse result) {
                        handleSignIn(result.getCredential());
                    }
                    @Override public void onError(GetCredentialException e) {
                        Toast.makeText(AccountActivity.this,
                                "Sign-in failed: " + e.getType() + " " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void handleSignIn(Credential credential) {
        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            try {
                GoogleIdTokenCredential g = GoogleIdTokenCredential.createFrom(
                        ((CustomCredential) credential).getData());
                firebaseAuthWithGoogle(g.getIdToken());
            } catch (Exception e) {
                Toast.makeText(this, "Couldn't read the Google credential", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Unexpected credential type", Toast.LENGTH_LONG).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                updateUi();
            } else {
                String msg = task.getException() == null ? "unknown error" : task.getException().getMessage();
                Toast.makeText(this, "Firebase sign-in failed: " + msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void signOut() {
        auth.signOut();
        CredentialManager.create(this).clearCredentialStateAsync(
                new ClearCredentialStateRequest(), new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void result) { }
                    @Override public void onError(ClearCredentialException e) { }
                });
        binding.tvResult.setText("");
        updateUi();
    }
}