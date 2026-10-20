package com.example.fluxora.db;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Payment payment);

    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    LiveData<List<Payment>> getAll();

    @Query("SELECT SUM(amount) FROM payments WHERE kind IN ('SENT','PAYMENT','CASH_OUT','RECHARGE') AND timestamp >= :since")
    LiveData<Double> totalSpentSince(long since);
}