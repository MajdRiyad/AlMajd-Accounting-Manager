package com.almajd.accounting;

import android.app.Application;

import com.almajd.accounting.db.DatabaseHelper;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DatabaseHelper.getInstance(this);
    }
}
