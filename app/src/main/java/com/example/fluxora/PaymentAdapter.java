package com.example.fluxora;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
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
        boolean spent = p.kind.equals("SENT") || p.kind.equals("PAYMENT")
                || p.kind.equals("CASH_OUT") || p.kind.equals("RECHARGE");
        h.b.tvWho.setText(p.counterparty == null || p.counterparty.isEmpty() ? p.kind : p.counterparty);
        h.b.tvMeta.setText(p.provider + " · " + p.kind + " · " + p.category + " · " + fmt.format(new Date(p.timestamp)));
        h.b.tvAmount.setText((spent ? "- Tk " : "+ Tk ") + String.format(Locale.US, "%.2f", p.amount));
        h.b.tvAmount.setTextColor(spent ? Color.parseColor("#C62828") : Color.parseColor("#2E7D32"));
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