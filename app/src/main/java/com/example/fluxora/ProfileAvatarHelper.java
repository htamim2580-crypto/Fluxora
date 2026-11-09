package com.example.fluxora;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ProfileAvatarHelper {

    private static final String PREFS = "fluxora_profile_extra";
    private static final String KEY_NAME = "student_name";
    private static final String AVATAR_FILE = "profile_avatar.png";

    public static String getName(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, "");
    }

    public static void setName(Context ctx, String name) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_NAME, name == null ? "" : name.trim())
                .apply();
    }

    /** Crops the picked image into a clean circle, saves it to internal storage, and returns the Bitmap. */
    public static Bitmap saveCircularAvatar(Context ctx, Uri uri) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            Bitmap raw = BitmapFactory.decodeStream(in);
            if (raw == null) return null;

            int size = Math.min(raw.getWidth(), raw.getHeight());
            int x = (raw.getWidth() - size) / 2;
            int y = (raw.getHeight() - size) / 2;
            Bitmap squared = Bitmap.createBitmap(raw, x, y, size, size);
            Bitmap scaled = Bitmap.createScaledBitmap(squared, 320, 320, true);

            Bitmap circle = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(circle);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            float r = 160f;
            canvas.drawCircle(r, r, r, paint);

            File file = new File(ctx.getFilesDir(), AVATAR_FILE);
            try (FileOutputStream out = new FileOutputStream(file)) {
                circle.compress(Bitmap.CompressFormat.PNG, 95, out);
            }
            return circle;
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap loadCircularAvatar(Context ctx) {
        File file = new File(ctx.getFilesDir(), AVATAR_FILE);
        if (!file.exists()) return null;
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }
}