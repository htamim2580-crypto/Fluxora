package com.example.fluxora;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;
import com.example.fluxora.db.AppDatabase;
import com.example.fluxora.db.Payment;
import com.example.fluxora.db.PaymentDao;

public class SmsImporter {
    public static void importAll(Context context) {
        Context app = context.getApplicationContext();
        String me = UserProfile.phone(app);

        AppDatabase.dbExecutor.execute(() -> {
            Cursor c = app.getContentResolver().query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE},
                    "address LIKE ? OR address LIKE ? OR body LIKE ?",
                    new String[]{"bKash", "NAGAD", "%has recharged your account%"},
                    null);
            if (c == null) return;
            PaymentDao dao = AppDatabase.get(app).paymentDao();
            while (c.moveToNext()) {
                Payment p = SmsParser.parse(c.getString(0), c.getString(1), c.getLong(2), me);
                if (p != null) dao.insert(p);
            }
            c.close();
        });
    }
}