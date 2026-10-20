package com.example.fluxora.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "payments", indices = {@Index(value = {"trxId"}, unique = true)})
public class Payment {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String provider;      // BKASH or NAGAD
    public String kind;          // SENT, RECEIVED, CASH_OUT, CASH_IN, PAYMENT, RECHARGE
    public double amount;
    public String counterparty;  // number or merchant name
    public String trxId;
    public double balance;
    public long timestamp;
    public String category;      // Food, Transport, Study, Other...
    public String rawSms;
}