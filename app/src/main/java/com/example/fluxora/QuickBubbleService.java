package com.example.fluxora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.RoutineEvent;

import java.util.List;

public class QuickBubbleService extends Service {

    public static final String CHANNEL_ID = "fluxora_quick_bubble";
    public static final String ACTION_STOP = "com.example.fluxora.STOP_BUBBLE";
    public static boolean isRunning = false;

    // 0 = Hidden Edge Line, 1 = Circular Bubble, 2 = Expanded AI Chat Box
    private int currentState = 0;

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private FrameLayout rootContainer;
    private View edgeHandleView;
    private View bubbleIconView;
    private View chatCardView;
    private EditText etQuickPrompt;
    private TextView btnQuickSend;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        createChannel();

        Intent stopIntent = new Intent(this, QuickBubbleService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 101, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 102, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_fluxora)
                .setColor(ContextCompat.getColor(this, R.color.primary))
                .setContentTitle("Fluxora Edge Assistant Active")
                .setContentText("Tap the subtle line on the right edge to update your routine")
                .setContentIntent(openPi)
                .addAction(0, "Stop", stopPi)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(9901, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(9901, notification);
        }

        if (Settings.canDrawOverlays(this)) {
            buildOverlayViews();
        }
    }

    private boolean isDarkMode() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void buildOverlayViews() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        rootContainer = new FrameLayout(this);

        // 1. STATE 0 VIEW: Subtle Edge Line Handle (Docked on the screen edge)
        FrameLayout edgeTouchWrapper = new FrameLayout(this);
        edgeTouchWrapper.setPadding(dp(10), dp(8), 0, dp(8)); // Invisible hit-target padding so it's easy to tap

        View edgePill = new View(this);
        GradientDrawable edgeBg = new GradientDrawable();
        edgeBg.setShape(GradientDrawable.RECTANGLE);
        edgeBg.setCornerRadii(new float[]{
                dp(8), dp(8), 0, 0,
                0, 0, dp(8), dp(8)
        });
        edgeBg.setColor(ContextCompat.getColor(this, R.color.primary));
        edgeBg.setAlpha(200); // Subtle semi-transparent sage line
        edgeBagStroke(edgeBg);
        edgePill.setBackground(edgeBg);

        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(dp(6), dp(68), Gravity.END | Gravity.CENTER_VERTICAL);
        edgeTouchWrapper.addView(edgePill, pillLp);
        edgeHandleView = edgeTouchWrapper;

        // 2. STATE 1 VIEW: Circular Fluxora Bubble
        ImageView bubbleIv = new ImageView(this);
        bubbleIv.setImageResource(R.drawable.ic_stat_fluxora);
        bubbleIv.setColorFilter(Color.WHITE);
        int pad = dp(13);
        bubbleIv.setPadding(pad, pad, pad, pad);

        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setShape(GradientDrawable.OVAL);
        bubbleBg.setColor(ContextCompat.getColor(this, R.color.primary));
        bubbleBg.setStroke(dp(2), ContextCompat.getColor(this, R.color.tertiary));
        bubbleIv.setBackground(bubbleBg);
        bubbleIv.setElevation(dp(8));

        FrameLayout bubbleWrapper = new FrameLayout(this);
        int bSize = dp(52);
        FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(bSize, bSize, Gravity.END | Gravity.CENTER_VERTICAL);
        bLp.setMargins(dp(6), dp(6), dp(10), dp(6));
        bubbleWrapper.addView(bubbleIv, bLp);
        bubbleIconView = bubbleWrapper;

        // 3. STATE 2 VIEW: Compact Quiet-Luxury AI Chat Card
        chatCardView = buildChatCard();

        // Add all 3 states into rootContainer
        rootContainer.addView(edgeHandleView);
        rootContainer.addView(bubbleIconView);
        rootContainer.addView(chatCardView);

        // Initial WindowManager Params (Docked to Top-Right, non-focusable so other apps work normally)
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 0;
        params.y = dp(240);

        attachTouchListeners();
        setState(0); // Start hidden as the thin edge line
        wm.addView(rootContainer, params);
    }

    private void edgeBagStroke(GradientDrawable bg) {
        bg.setStroke(dp(1), ContextCompat.getColor(this, R.color.tertiary));
    }

    private View buildChatCard() {
        boolean dark = isDarkMode();
        int cardBgColor = ContextCompat.getColor(this, dark ? R.color.surface_dark : R.color.surface_light);
        int inputBgColor = ContextCompat.getColor(this, dark ? R.color.background_dark : R.color.background_light);
        int textColor = ContextCompat.getColor(this, dark ? R.color.text_primary_dark : R.color.text_primary_light);
        int subTextColor = ContextCompat.getColor(this, dark ? R.color.text_secondary_dark : R.color.text_secondary_light);
        int outlineColor = ContextCompat.getColor(this, dark ? R.color.outline_dark : R.color.outline_light);
        int primaryColor = ContextCompat.getColor(this, R.color.primary);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable cardShape = new GradientDrawable();
        cardShape.setShape(GradientDrawable.RECTANGLE);
        cardShape.setCornerRadius(dp(22));
        cardShape.setColor(cardBgColor);
        cardShape.setStroke(dp(1), outlineColor);
        card.setBackground(cardShape);
        card.setElevation(dp(10));

        // Header Row: Title + Minimize Button
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Fluxora AI Quick Update");
        tvTitle.setTextColor(textColor);
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(tvTitle, titleLp);

        TextView btnClose = new TextView(this);
        btnClose.setText("✕");
        btnClose.setTextColor(subTextColor);
        btnClose.setTextSize(15f);
        btnClose.setPadding(dp(10), dp(2), dp(4), dp(2));
        btnClose.setOnClickListener(v -> {
            hideKeyboard();
            setState(0); // Collapse straight back to the hidden edge line
        });
        headerRow.addView(btnClose);
        card.addView(headerRow);

        // Input Row: EditText + Send Pill Button
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(12), dp(6), dp(6), dp(6));

        GradientDrawable inputShape = new GradientDrawable();
        inputShape.setShape(GradientDrawable.RECTANGLE);
        inputShape.setCornerRadius(dp(999));
        inputShape.setColor(inputBgColor);
        inputShape.setStroke(dp(1), outlineColor);
        inputRow.setBackground(inputShape);

        LinearLayout.LayoutParams inputRowLp = new LinearLayout.LayoutParams(dp(280), LinearLayout.LayoutParams.WRAP_CONTENT);
        inputRowLp.topMargin = dp(10);

        etQuickPrompt = new EditText(this);
        etQuickPrompt.setBackground(null);
        etQuickPrompt.setHint("e.g. MATH 2207 cancelled tomorrow...");
        etQuickPrompt.setHintTextColor(subTextColor);
        etQuickPrompt.setTextColor(textColor);
        etQuickPrompt.setTextSize(13f);
        etQuickPrompt.setMaxLines(2);
        etQuickPrompt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        inputRow.addView(etQuickPrompt, etLp);

        btnQuickSend = new TextView(this);
        btnQuickSend.setText("Update");
        btnQuickSend.setTextColor(Color.WHITE);
        btnQuickSend.setTextSize(12f);
        btnQuickSend.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        btnQuickSend.setGravity(Gravity.CENTER);
        btnQuickSend.setPadding(dp(14), dp(8), dp(14), dp(8));

        GradientDrawable btnShape = new GradientDrawable();
        btnShape.setShape(GradientDrawable.RECTANGLE);
        btnShape.setCornerRadius(dp(999));
        btnShape.setColor(primaryColor);
        btnQuickSend.setBackground(btnShape);
        btnQuickSend.setOnClickListener(v -> submitQuickChat());

        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sendLp.leftMargin = dp(6);
        inputRow.addView(btnQuickSend, sendLp);

        card.addView(inputRow, inputRowLp);

        FrameLayout wrapper = new FrameLayout(this);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(12), dp(8), dp(12), dp(8));
        wrapper.addView(card, cardLp);
        return wrapper;
    }

    private void attachTouchListeners() {
        // Dragging vertically along the edge or tapping for both State 0 (Edge Line) & State 1 (Bubble)
        View.OnTouchListener edgeOrBubbleTouch = new View.OnTouchListener() {
            private int initialY;
            private float touchY;
            private boolean moved;
            private long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = params.y;
                        touchY = event.getRawY();
                        moved = false;
                        downTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dy = (int) (event.getRawY() - touchY);
                        if (Math.abs(dy) > dp(6)) {
                            moved = true;
                            params.y = initialY + dy;
                            wm.updateViewLayout(rootContainer, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        long elapsed = System.currentTimeMillis() - downTime;
                        if (!moved) {
                            if (v == edgeHandleView) {
                                // Tapped hidden edge line -> Show circular bubble!
                                setState(1);
                            } else if (v == bubbleIconView) {
                                if (elapsed > 500) {
                                    // Long-press on bubble -> Hide back into the edge line
                                    setState(0);
                                } else {
                                    // Single tap on bubble -> Open mini AI chat card right here!
                                    setState(2);
                                }
                            }
                        }
                        return true;
                }
                return false;
            }
        };

        edgeHandleView.setOnTouchListener(edgeOrBubbleTouch);
        bubbleIconView.setOnTouchListener(edgeOrBubbleTouch);
    }

    private void setState(int newState) {
        currentState = newState;
        edgeHandleView.setVisibility(newState == 0 ? View.VISIBLE : View.GONE);
        bubbleIconView.setVisibility(newState == 1 ? View.VISIBLE : View.GONE);
        chatCardView.setVisibility(newState == 2 ? View.VISIBLE : View.GONE);

        if (newState == 2) {
            // Enable window focus so the soft keyboard can type into etQuickPrompt
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            params.x = dp(8);
            if (rootContainer.isAttachedToWindow()) {
                wm.updateViewLayout(rootContainer, params);
            }
            etQuickPrompt.requestFocus();
            mainHandler.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etQuickPrompt, InputMethodManager.SHOW_IMPLICIT);
            }, 120);
        } else {
            // Make non-focusable again so background apps receive all touches and Back button presses
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            params.x = 0;
            if (rootContainer.isAttachedToWindow()) {
                wm.updateViewLayout(rootContainer, params);
            }
        }
    }

    private void hideKeyboard() {
        if (etQuickPrompt != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etQuickPrompt.getWindowToken(), 0);
        }
    }

    /** Sends the typed message through the exact same AI pipeline as MainActivity's chatBar without opening the app. */
    private void submitQuickChat() {
        String msg = etQuickPrompt.getText().toString().trim();
        if (msg.isEmpty()) return;

        if (!AiPrefs.enabled()) {
            Toast.makeText(this, "AI features are turned off in Fluxora Settings.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!NetworkUtils.requireInternet(this)) return;

        btnQuickSend.setEnabled(false);
        btnQuickSend.setText("...");

        AppDatabase db = AppDatabase.get(getApplicationContext());
        AppDatabase.dbExecutor.execute(() -> {
            List<RoutineEvent> currentEvents = db.routineDao().getAllNow();
            String prompt = PromptBuilder.update(QuickBubbleService.this, msg, currentEvents, false);

            mainHandler.post(() -> AiClient.ask(prompt, null, new AiClient.Callback() {
                @Override
                public void onSuccess(String text) {
                    btnQuickSend.setEnabled(true);
                    btnQuickSend.setText("Update");

                    List<RoutineEvent> parsed = RoutineParser.tryParse(text, "CHAT");
                    if (parsed == null) {
                        Toast.makeText(QuickBubbleService.this, "Couldn't understand the AI reply", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (parsed.isEmpty()) {
                        Toast.makeText(QuickBubbleService.this, "Nothing to add from that message", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AppDatabase.dbExecutor.execute(() -> {
                        for (RoutineEvent e : parsed) {
                            db.routineDao().insert(e);
                        }
                        ClassScheduler.scheduleAllAsync(getApplicationContext());
                        mainHandler.post(() -> {
                            etQuickPrompt.setText("");
                            hideKeyboard();
                            setState(0); // Collapse back to the subtle edge line!
                            Toast.makeText(QuickBubbleService.this,
                                    "Added " + parsed.size() + " update(s) to your routine ✓",
                                    Toast.LENGTH_LONG).show();
                        });
                    });
                }

                @Override
                public void onError(String message) {
                    btnQuickSend.setEnabled(true);
                    btnQuickSend.setText("Update");
                    Toast.makeText(QuickBubbleService.this, message, Toast.LENGTH_LONG).show();
                }
            }));
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (wm != null && rootContainer != null && rootContainer.isAttachedToWindow()) {
            wm.removeView(rootContainer);
            rootContainer = null;
        }
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Floating Quick-Add Bubble", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}