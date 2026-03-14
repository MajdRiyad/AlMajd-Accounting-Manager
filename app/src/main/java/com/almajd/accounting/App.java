package com.almajd.accounting;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;

import com.almajd.accounting.db.DatabaseHelper;

import java.util.Locale;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DatabaseHelper.getInstance(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(forceArabicLocale(base));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        forceArabicLocale(this);
    }

    /**
     * Forces Arabic locale and RTL layout direction on the given context.
     * This ensures the app always displays in Arabic RTL regardless of device settings.
     */
    public static Context forceArabicLocale(Context context) {
        Locale arabic = new Locale("ar");
        Locale.setDefault(arabic);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        config.setLocale(arabic);
        config.setLocales(new LocaleList(arabic));
        config.setLayoutDirection(arabic);

        return context.createConfigurationContext(config);
    }
}
