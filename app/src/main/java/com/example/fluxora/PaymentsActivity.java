package com.example.fluxora;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.fluxora.databinding.ActivityPaymentsBinding;
import com.example.fluxora.db.AppDatabase;
import java.util.Calendar;
import java.util.Locale;

public class PaymentsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityPaymentsBinding binding = ActivityPaymentsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AppDatabase db = AppDatabase.get(this);
        PaymentAdapter adapter = new PaymentAdapter();
        binding.rvPayments.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPayments.setAdapter(adapter);
        db.paymentDao().getAll().observe(this, adapter::setItems);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        db.paymentDao().totalSpentSince(cal.getTimeInMillis()).observe(this, total ->
                binding.tvTotal.setText("Spent this month: Tk "
                        + String.format(Locale.US, "%.2f", total == null ? 0.0 : total)));
    }
}