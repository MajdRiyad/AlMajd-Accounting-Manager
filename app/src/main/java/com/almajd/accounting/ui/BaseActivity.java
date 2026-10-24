package com.almajd.accounting.ui;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import com.almajd.accounting.App;

/**
 * Base activity that forces Arabic locale and RTL layout direction.
 * All activities in the app should extend this class.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(App.forceArabicLocale(newBase));
    }
}
