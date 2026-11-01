package com.example.fluxora;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fluxora.databinding.ItemEventBinding;
import com.example.fluxora.db.RoutineEvent;
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

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemEventBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        RoutineEvent e = items.get(position);
        h.b.tvStart.setText(e.startTime);
        h.b.tvEnd.setText(e.endTime);
        h.b.tvTitle.setText(e.title);

        StringBuilder sub = new StringBuilder();
        if (e.room != null && !e.room.isEmpty()) sub.append("Room ").append(e.room);
        if (e.details != null && !e.details.isEmpty()) {
            if (sub.length() > 0) sub.append(" · ");
            sub.append(e.details);
        }
        h.b.tvRoom.setText(sub.toString());
        h.b.tvRoom.setVisibility(sub.length() == 0 ? View.GONE : View.VISIBLE);

        int[] s = stats.get(e.title);
        if (s == null || s[1] == 0) {
            h.b.tvAttendance.setText("Attendance: not recorded yet");
        } else {
            h.b.tvAttendance.setText(String.format(Locale.US, "Attended %d/%d (%d%%)",
                    s[0], s[1], Math.round(100f * s[0] / s[1])));
        }

        h.itemView.setOnClickListener(v -> listener.onClick(e));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(e);
            return true;
        });
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