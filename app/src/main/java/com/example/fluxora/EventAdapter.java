package com.example.fluxora;

import android.content.Context;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fluxora.databinding.ItemEventBinding;
import com.example.fluxora.db.RoutineEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.VH> {

    public interface Listener {
        void onClick(RoutineEvent event);
        void onLongClick(RoutineEvent event);
    }

    private List<RoutineEvent> items = new ArrayList<>();
    private Map<String, int[]> stats = new HashMap<>();   // course title -> {present, total}
    private final Listener listener;

    public EventAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<RoutineEvent> newItems) {
        items = newItems;
        notifyDataSetChanged();
    }

    public void setStats(Map<String, int[]> newStats) {
        stats = newStats;
        notifyDataSetChanged();
    }

    public static String typeLabel(String type) {
        if (type == null) return "Item";
        switch (type) {
            case "EXTRA_CLASS": return "Extra class";
            case "CANCELLED": return "Cancelled";
            case "ASSIGNMENT": return "Assignment";
            case "DEADLINE": return "Deadline";
            case "CLASS": return "Class";
            default: return "Note";
        }
    }

    private static String shortDate(String iso) {
        try {
            return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd MMM", Locale.US));
        } catch (Exception e) {
            return iso;
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemEventBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        RoutineEvent e = items.get(position);
        Context ctx = h.itemView.getContext();
        boolean dated = e.dayOfWeek == 0 && e.date != null;
        boolean isCancelled = "CANCELLED".equals(e.type);

        h.b.tvTitle.setText(e.title);

        // Subtle strikethrough & muted opacity for cancelled classes
        if (isCancelled) {
            h.b.tvTitle.setPaintFlags(h.b.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.b.tvTitle.setAlpha(0.6f);
        } else {
            h.b.tvTitle.setPaintFlags(h.b.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            h.b.tvTitle.setAlpha(1.0f);
        }

        StringBuilder sub = new StringBuilder();
        if (e.room != null && !e.room.isEmpty()) sub.append("Room ").append(e.room);
        if (e.details != null && !e.details.isEmpty()) {
            if (sub.length() > 0) sub.append(" · ");
            sub.append(e.details);
        }
        h.b.tvRoom.setText(sub.toString());
        h.b.tvRoom.setVisibility(sub.length() == 0 ? View.GONE : View.VISIBLE);

        int[] s = stats.get(e.title);
        if (dated) {
            h.b.tvStart.setText(shortDate(e.date));
            h.b.tvEnd.setText(e.startTime == null ? "" : e.startTime);
            String label = typeLabel(e.type);
            if ("WHATSAPP".equals(e.source)) label += " · via WhatsApp";
            if ("EXTRA_CLASS".equals(e.type) && s != null && s[1] > 0) {
                label += String.format(Locale.US, " · %d/%d (%d%%)",
                        s[0], s[1], Math.round(100f * s[0] / s[1]));
            }
            h.b.tvAttendance.setText(label);

            // Color-code special dated events with semantic botanical accents
            if (isCancelled) {
                h.b.tvAttendance.setTextColor(ContextCompat.getColor(ctx, R.color.danger));
            } else if ("DEADLINE".equals(e.type) || "ASSIGNMENT".equals(e.type)) {
                h.b.tvAttendance.setTextColor(ContextCompat.getColor(ctx, R.color.warning));
            } else {
                h.b.tvAttendance.setTextColor(getThemeColor(ctx, com.google.android.material.R.attr.colorPrimary));
            }
        } else {
            h.b.tvStart.setText(e.startTime);
            h.b.tvEnd.setText(e.endTime);
            if (s == null || s[1] == 0) {
                h.b.tvAttendance.setText("No attendance recorded yet");
                h.b.tvAttendance.setTextColor(getThemeColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant));
            } else {
                int pct = Math.round(100f * s[0] / s[1]);
                h.b.tvAttendance.setText(String.format(Locale.US, "Attended %d/%d  ·  %d%%", s[0], s[1], pct));
                h.b.tvAttendance.setTextColor(getThemeColor(ctx, com.google.android.material.R.attr.colorPrimary));
            }
        }

        h.itemView.setOnClickListener(v -> listener.onClick(e));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(e);
            return true;
        });
    }

    private static int getThemeColor(Context context, @AttrRes int attr) {
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, tv, true)) {
            return tv.data;
        }
        return 0;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemEventBinding b;
        VH(ItemEventBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}