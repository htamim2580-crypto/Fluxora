package com.example.fluxora;

import android.app.Application;

public class FluxoraApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AiPrefs.init(this);
    }
}