package com.example.fluxora;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.widget.Toast;

public class NetworkUtils {

    public static boolean isOnline(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network net = cm.getActiveNetwork();
        if (net == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(net);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /** Returns true if online; otherwise shows a bottom Toast and returns false. */
    public static boolean requireInternet(Context ctx) {
        if (!isOnline(ctx)) {
            Toast.makeText(ctx, "No internet connection. Please connect and try again.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}