package com.example.fluxora;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
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

        if (d == null) {
            tv.setText("");
            tv.setBackgroundColor(Color.TRANSPARENT);
            h.itemView.setOnClickListener(null);
            h.itemView.setClickable(false);
            return;
        }

        tv.setText(String.valueOf(d.getDayOfMonth()));
        tv.setTypeface(null, d.equals(LocalDate.now()) ? Typeface.BOLD : Typeface.NORMAL);

        int[] s = summary.get(d.toString());
        if (s == null) {
            tv.setBackgroundColor(Color.TRANSPARENT);
            tv.setTextColor(Color.GRAY);
        } else {
            if (s[1] == 0) tv.setBackgroundColor(Color.parseColor("#A5D6A7"));        // all present
            else if (s[0] == 0) tv.setBackgroundColor(Color.parseColor("#EF9A9A"));   // all absent
            else tv.setBackgroundColor(Color.parseColor("#FFCC80"));                  // mixed
            tv.setTextColor(Color.BLACK);
        }

        h.itemView.setOnClickListener(v -> listener.onDay(d));
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