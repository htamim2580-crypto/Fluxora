package com.example.fluxora;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fluxora.databinding.ItemPaymentBinding;
import com.example.fluxora.db.Payment;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PaymentAdapter extends RecyclerView.Adapter<PaymentAdapter.VH> {

    private List<Payment> items = new ArrayList<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM, HH:mm", Locale.US);

    public void setItems(List<Payment> newItems) {
        items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemPaymentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Payment p = items.get(position);
        Context ctx = h.itemView.getContext();

        boolean spent = "SENT".equals(p.kind) || "PAYMENT".equals(p.kind)
                || "CASH_OUT".equals(p.kind) || "RECHARGE".equals(p.kind);

        String who = (p.counterparty == null || p.counterparty.trim().isEmpty())
                ? formatKind(p.kind)
                : p.counterparty;
        h.b.tvWho.setText(who);

        String meta = p.provider + "  ·  " + formatKind(p.kind)
                + (p.category != null && !p.category.isEmpty() ? "  ·  " + p.category : "")
                + "\n" + fmt.format(new Date(p.timestamp));
        h.b.tvMeta.setText(meta);

        h.b.tvAmount.setText((spent ? "- Tk " : "+ Tk ") + String.format(Locale.US, "%.2f", p.amount));

        // Soft botanical pill background for amount badge
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.RECTANGLE);
        badge.setCornerRadius(999f);

        if (spent) {
            badge.setColor(ContextCompat.getColor(ctx, R.color.danger_container));
            h.b.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.on_danger_container));
        } else {
            badge.setColor(ContextCompat.getColor(ctx, R.color.success_container));
            h.b.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.on_success_container));
        }
        h.b.tvAmount.setBackground(badge);
    }

    private static String formatKind(String kind) {
        if (kind == null || kind.isEmpty()) return "Transaction";
        String clean = kind.replace('_', ' ').toLowerCase(Locale.US);
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemPaymentBinding b;
        VH(ItemPaymentBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}