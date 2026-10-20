package com.example.fluxora;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fluxora.databinding.ItemEventBinding;
import com.example.fluxora.db.RoutineEvent;
import java.util.ArrayList;
import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.VH> {

    public interface OnEventLongClick {
        void onLongClick(RoutineEvent event);
    }

    private static final String[] DAYS =
            {"", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private List<RoutineEvent> items = new ArrayList<>();
    private final OnEventLongClick listener;

    public EventAdapter(OnEventLongClick listener) {
        this.listener = listener;
    }

    public void setItems(List<RoutineEvent> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEventBinding b = ItemEventBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        RoutineEvent e = items.get(position);
        holder.b.tvTitle.setText(e.title);
        String day = (e.dayOfWeek >= 1 && e.dayOfWeek <= 7) ? DAYS[e.dayOfWeek] : e.date;
        holder.b.tvTime.setText(day + "  " + e.startTime + " - " + e.endTime);
        holder.b.tvRoom.setText(e.room == null || e.room.isEmpty() ? "" : "Room: " + e.room);
        holder.itemView.setOnLongClickListener(v -> {
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