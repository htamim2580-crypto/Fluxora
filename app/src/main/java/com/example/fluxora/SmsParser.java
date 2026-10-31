package com.example.fluxora;

import com.example.fluxora.db.Payment;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {

    private static final Pattern AMOUNT = Pattern.compile("Tk\\s?([\\d,]+(?:\\.\\d+)?)");
    private static final Pattern BALANCE = Pattern.compile("Balance\\s+Tk\\s?([\\d,]+(?:\\.\\d+)?)");
    private static final Pattern TRX = Pattern.compile("TrxID\\s+(\\w+)");
    private static final Pattern TIME = Pattern.compile("at\\s+(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2})");
    private static final Pattern PHONE = Pattern.compile("(?:from|to|for)\\s+(01\\d{9})");
    private static final Pattern MERCHANT = Pattern.compile("Payment of Tk\\s?[\\d,.]+ to (.+?) is successful");

    // Operator SMS: "Transaction ID is 000324753538 . 8801513925315 has recharged your account with 20.00 Taka."
    private static final Pattern OP_RECHARGE = Pattern.compile(
            "Transaction ID is\\s+(\\w+)\\s*\\.\\s*(\\d{10,13}) has recharged your account with ([\\d,]+(?:\\.\\d+)?) Taka",
            Pattern.CASE_INSENSITIVE);

    /** Returns null if the SMS is not a bKash/Nagad transaction. */
    public static Payment parse(String sender, String body, long receivedAt, String myNumber) {
        if (body == null) return null;

        Matcher op = OP_RECHARGE.matcher(body);
        if (op.find()) {
            String digits = myNumber == null ? "" : myNumber.replaceAll("\\D", "");
            boolean mine = digits.length() >= 10
                    && op.group(2).endsWith(digits.substring(digits.length() - 10));
            Payment p = new Payment();
            p.provider = mine ? "BKASH" : "OPERATOR";
            p.kind = mine ? "RECHARGE" : "RECHARGE_RECEIVED";
            p.amount = toDouble(op.group(3));
            p.counterparty = "Mobile recharge";
            p.trxId = op.group(1);
            p.balance = 0;
            p.timestamp = receivedAt;
            p.category = "Recharge";
            p.rawSms = body;
            return p;
        }

        if (sender == null) return null;

        String provider;
        if (sender.equalsIgnoreCase("bKash")) provider = "BKASH";
        else if (sender.equalsIgnoreCase("NAGAD")) provider = "NAGAD";
        else return null;

        Matcher trx = TRX.matcher(body);
        Matcher amt = AMOUNT.matcher(body);
        if (!trx.find() || !amt.find()) return null;

        String lower = body.toLowerCase(Locale.US);
        String kind;
        if (lower.contains("recharge")) kind = "RECHARGE";
        else if (lower.contains("cash in")) kind = "CASH_IN";
        else if (lower.contains("cash out")) kind = "CASH_OUT";
        else if (lower.startsWith("payment of")) kind = "PAYMENT";
        else if (lower.contains("send money") || lower.contains("you have sent")) kind = "SENT";
        else if (lower.contains("received")) kind = "RECEIVED";
        else kind = "OTHER";

        String who = "";
        Matcher m = MERCHANT.matcher(body);
        if (m.find()) who = m.group(1);
        else {
            m = PHONE.matcher(body);
            if (m.find()) who = m.group(1);
        }

        Payment p = new Payment();
        p.provider = provider;
        p.kind = kind;
        p.amount = toDouble(amt.group(1));
        p.counterparty = who;
        p.trxId = trx.group(1);

        Matcher bal = BALANCE.matcher(body);
        p.balance = bal.find() ? toDouble(bal.group(1)) : 0;

        p.timestamp = receivedAt;
        Matcher t = TIME.matcher(body);
        if (t.find()) {
            try {
                p.timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
                        .parse(t.group(1)).getTime();
            } catch (ParseException ignored) { }
        }

        p.category = categorize(kind, who);
        p.rawSms = body;
        return p;
    }

    private static double toDouble(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }

    private static String categorize(String kind, String who) {
        String w = who == null ? "" : who.toLowerCase(Locale.US);
        if (kind.equals("RECHARGE")) return "Recharge";
        if (w.contains("foodpanda") || w.contains("pathao food") || w.contains("hungrynaki")) return "Food";
        if (w.contains("pathao") || w.contains("uber") || w.contains("obhai")) return "Transport";
        return "Other";
    }
}