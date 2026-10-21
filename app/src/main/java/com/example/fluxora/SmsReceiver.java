package com.example.fluxora;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.Payment;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;

        String sender = msgs[0].getOriginatingAddress();
        StringBuilder body = new StringBuilder();
        for (SmsMessage m : msgs) body.append(m.getMessageBody());

        Payment p = SmsParser.parse(sender, body.toString());
        if (p == null) return;

        PendingResult result = goAsync();
        AppDatabase.dbExecutor.execute(() -> {
            try {
                AppDatabase.get(context).paymentDao().insert(p);
            } finally {
                result.finish();
            }
        });
    }
}