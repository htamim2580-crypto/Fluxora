package com.example.fluxora;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiClient {

    // If you get "model not found", use a model name that AI Studio currently lists.
    private static final String[] MODELS = {"gemini-3.6-flash", "gemini-3.5-flash"};

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String text);
        void onError(String message);
    }

    private static String endpoint(String model) {
        return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
    }

    /** Normal entry point: respects the AI switch and uses the user's own key if set. */
    public static void ask(String prompt, Bitmap image, Callback cb) {
        if (!AiPrefs.enabled()) {
            MAIN.post(() -> cb.onError("AI is turned off. Turn it on in Settings."));
            return;
        }
        String key = AiPrefs.effectiveKey();
        if (key == null || key.isEmpty()) {
            MAIN.post(() -> cb.onError("No Gemini key. Add one in Settings."));
            return;
        }
        askWithKey(key, prompt, image, cb);
    }

    /** Used directly by the Settings screen to test a key before saving it. */
    public static void askWithKey(String key, String prompt, Bitmap image, Callback cb) {
        EXEC.execute(() -> {
            try {
                String text = requestWithRetry(prompt, image, key);
                MAIN.post(() -> cb.onSuccess(text));
            } catch (Exception e) {
                String msg = friendly(e.getMessage());
                MAIN.post(() -> cb.onError(msg));
            }
        });
    }

    private static String friendly(String m) {
        if (m == null) return "Network error. Check your connection.";
        if (m.startsWith("API error 503") || m.startsWith("API error 429")) {
            return "AI is busy right now. Try again in a minute.";
        }
        if (m.contains("API_KEY_INVALID") || m.contains("API key not valid")) {
            return "That Gemini key isn't valid. Check it in Settings.";
        }
        if (m.startsWith("API error 403")) {
            return "Gemini refused the key (403). Check it in Settings.";
        }
        if (m.startsWith("API error 404")) {
            return "The AI model isn't available. The app may need an update.";
        }
        return m;
    }

    private static String requestWithRetry(String prompt, Bitmap image, String key) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            String model = MODELS[attempt % MODELS.length];
            try {
                return request(prompt, image, model, key);
            } catch (IOException e) {
                last = e;
                String m = String.valueOf(e.getMessage());
                boolean retryable = m.startsWith("API error 503")
                        || m.startsWith("API error 429")
                        || m.startsWith("API error 500");
                if (!retryable) throw e;
                Thread.sleep(2000L * (attempt + 1));
            }
        }
        throw last;
    }

    private static String request(String prompt, Bitmap image, String model, String key) throws Exception {
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", prompt));
        if (image != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.JPEG, 85, out);
            String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            parts.put(new JSONObject().put("inline_data",
                    new JSONObject().put("mime_type", "image/jpeg").put("data", b64)));
        }

        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("parts", parts)))
                .put("generationConfig", new JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("temperature", 0.1));

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint(model)).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-goog-api-key", key);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String resp = readAll(is);
        if (code < 200 || code >= 300) throw new IOException("API error " + code + ": " + resp);

        return new JSONObject(resp)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text");
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}