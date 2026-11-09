package com.example.fluxora;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class AccountActivity extends AppCompatActivity {

    private ActivityAccountBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        binding.btnSignIn.setOnClickListener(v -> signIn());
        binding.btnSignOut.setOnClickListener(v -> signOut());
        binding.btnBackup.setOnClickListener(v -> backup());
        binding.btnRestore.setOnClickListener(v -> confirmRestore());
        updateUi();
    }

    private void updateUi() {
        FirebaseUser user = auth.getCurrentUser();
        boolean signed = user != null;
        if (!signed) {
            binding.tvStatus.setText("Not signed in. Sign in to back up your routine and attendance.");
        } else {
            String name = user.getDisplayName() == null ? "" : user.getDisplayName() + "\n";
            binding.tvStatus.setText("Signed in as\n" + name + user.getEmail());
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

    // ---------- backup / restore (with offline check) ----------

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

        new AlertDialog.Builder(this)
                .setTitle("Restore from backup?")
                .setMessage("This replaces the routine and attendance on this phone with your last backup.")
                .setPositiveButton("Restore", (d, w) -> restore())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- sign in / out (with offline check) ----------

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