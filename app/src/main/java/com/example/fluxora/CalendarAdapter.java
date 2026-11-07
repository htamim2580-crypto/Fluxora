package com.example.fluxora;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fluxora.databinding.ItemDayBinding;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.VH> {

    public interface OnDayClick {
        void onDay(LocalDate date);
    }

    private List<LocalDate> days = new ArrayList<>();                 // null = empty cell before day 1
    private Map<String, int[]> summary = new HashMap<>();             // date -> {present, absent}
    private final OnDayClick listener;

    public CalendarAdapter(OnDayClick listener) {
        this.listener = listener;
    }

    public void setData(List<LocalDate> days, Map<String, int[]> summary) {
        this.days = days;
        this.summary = summary;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemDayBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        LocalDate d = days.get(position);
        TextView tv = h.b.tvDay;
        Context ctx = tv.getContext();

        // Remove the outer FrameLayout's default background so our rounded pill on tvDay controls the cell
        h.itemView.setBackgroundColor(Color.TRANSPARENT);

        if (d == null) {
            tv.setText("");
            tv.setBackground(null);
            h.itemView.setOnClickListener(null);
            h.itemView.setClickable(false);
            return;
        }

        boolean isToday = d.equals(LocalDate.now());
        tv.setText(String.valueOf(d.getDayOfMonth()));
        tv.setTypeface(null, isToday ? Typeface.BOLD : Typeface.NORMAL);

        GradientDrawable cellBg = new GradientDrawable();
        cellBg.setShape(GradientDrawable.RECTANGLE);
        cellBg.setCornerRadius(dpToPx(ctx, 14f));

        int[] s = summary.get(d.toString());
        if (s == null) {
            // Subtle surface fill for unrecorded days (adapts to Light & Dark mode)
            cellBg.setColor(getThemeColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant));
            tv.setTextColor(getThemeColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant));
        } else {
            if (s[1] == 0) {
                // All Present -> Botanical Sage Green
                cellBg.setColor(ContextCompat.getColor(ctx, R.color.success_container));
                tv.setTextColor(ContextCompat.getColor(ctx, R.color.on_success_container));
            } else if (s[0] == 0) {
                // All Absent -> Soft Terracotta Red
                cellBg.setColor(ContextCompat.getColor(ctx, R.color.danger_container));
                tv.setTextColor(ContextCompat.getColor(ctx, R.color.on_danger_container));
            } else {
                // Mixed -> Warm Amber / Champagne Gold
                cellBg.setColor(ContextCompat.getColor(ctx, R.color.warning_container));
                tv.setTextColor(ContextCompat.getColor(ctx, R.color.on_warning_container));
            }
        }

        // Highlight Today with a refined Brushed Gold border ring
        if (isToday) {
            cellBg.setStroke(dpToPx(ctx, 2f), ContextCompat.getColor(ctx, R.color.tertiary));
            if (s == null) {
                tv.setTextColor(getThemeColor(ctx, com.google.android.material.R.attr.colorOnSurface));
            }
        }

        tv.setBackground(cellBg);
        h.itemView.setClickable(true);
        h.itemView.setOnClickListener(v -> listener.onDay(d));
    }

    private static int getThemeColor(Context context, @AttrRes int attr) {
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, tv, true)) {
            return tv.data;
        }
        return Color.GRAY;
    }

    private static int dpToPx(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemDayBinding b;
        VH(ItemDayBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}