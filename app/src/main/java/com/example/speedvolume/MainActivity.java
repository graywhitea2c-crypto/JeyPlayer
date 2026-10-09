package com.example.speedvolume;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status, speed, volume, hint;
    private Button toggle;
    private boolean running = false;
    private static final int REQUEST_LOCATION = 10;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setGravity(Gravity.TOP);
        root.setBackgroundColor(Color.rgb(247,249,252));

        TextView title = text("کنترل هوشمند صدا", 26, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = text("تنظیم خودکار صدای رسانه با سرعت GPS", 15, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams subp = matchWrap(); subp.topMargin = dp(8); subp.bottomMargin = dp(24);
        root.addView(subtitle, subp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackgroundColor(Color.WHITE);
        card.setElevation(dp(2));

        status = text("وضعیت: متوقف", 18, true);
        speed = text("سرعت GPS: -- km/h", 21, true);
        volume = text("صدای هدف: 20٪", 24, true);
        hint = text("برای شروع، GPS و مجوز مکان‌یابی را فعال کنید.", 14, false);
        hint.setTextColor(Color.DKGRAY);
        for (TextView v : new TextView[]{status, speed, volume, hint}) {
            LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(12); card.addView(v, p);
        }
        root.addView(card, matchWrap());

        toggle = new Button(this);
        toggle.setText("شروع کنترل خودکار");
        LinearLayout.LayoutParams bp = matchWrap(); bp.topMargin = dp(22);
        root.addView(toggle, bp);
        toggle.setOnClickListener(v -> switchRunning());

        TextView details = text("کمتر از ۲۰ km/h: ۲۰٪\nبین ۲۰ تا ۸۰: افزایش تدریجی\n۸۰ km/h و بالاتر: ۱۰۰٪\n\nاین برنامه صدای رسانه/موسیقی را کنترل می‌کند. درصدها سطح صدای سیستم نسبت به حداکثر فعلی هستند.", 15, false);
        details.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams dp = matchWrap(); dp.topMargin = dp(22);
        root.addView(details, dp);

        setContentView(root);
    }

    private void switchRunning() {
        if (!running) {
            if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
                hint.setText("برای استفاده، مجوز مکان‌یابی را تأیید کنید.");
                return;
            }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            }
            Intent i = new Intent(this, VolumeService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            running = true;
            status.setText("وضعیت: فعال");
            toggle.setText("توقف کنترل خودکار");
            hint.setText("در حال دریافت سرعت از GPS…");
        } else {
            stopService(new Intent(this, VolumeService.class));
            running = false;
            status.setText("وضعیت: متوقف");
            toggle.setText("شروع کنترل خودکار");
            hint.setText("کنترل خودکار متوقف شد.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            hint.setText("مجوز مکان‌یابی فعال شد؛ برای شروع دکمه را بزنید.");
        } else if (requestCode == REQUEST_LOCATION) {
            hint.setText("بدون مجوز مکان‌یابی، سرعت GPS در دسترس نیست.");
        }
    }

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(Color.rgb(24,35,52));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.RIGHT);
        return t;
    }
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }
    private int dp(float x) { return (int)(x * getResources().getDisplayMetrics().density + 0.5f); }
}
