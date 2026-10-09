package com.example.speedvolume;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class VolumeService extends Service implements LocationListener {

    private static final String TAG = "VolumeService";
    private static final String CHANNEL_ID = "speed_volume_channel";
    private static final int NOTIFICATION_ID = 1207;

    private LocationManager locationManager;
    private AudioManager audioManager;

    private float smoothedSpeedKmh = 0f;
    private boolean hasSpeedSample = false;
    private int lastAppliedPercent = -1;

    @Override
    public void onCreate() {
        super.onCreate();

        audioManager =
                (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        createNotificationChannel();

        startForeground(
                NOTIFICATION_ID,
                buildNotification("در انتظار دریافت سرعت GPS...")
        );

        startGpsUpdates();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void startGpsUpdates() {

        if (locationManager == null) {
            stopSelf();
            return;
        }

        boolean finePermission =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;

        boolean coarsePermission =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;

        if (!finePermission && !coarsePermission) {
            updateNotification("مجوز مکان داده نشده است");
            stopSelf();
            return;
        }

        try {
            if (locationManager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER)) {

                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        0f,
                        this,
                        Looper.getMainLooper()
                );

            } else {
                updateNotification("GPS خاموش است؛ GPS را روشن کنید");
            }

            if (locationManager.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER)) {

                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2000L,
                        0f,
                        this,
                        Looper.getMainLooper()
                );
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Location permission error", e);
            updateNotification("دسترسی مکان در دسترس نیست");
            stopSelf();

        } catch (Exception e) {
            Log.e(TAG, "Unable to request location updates", e);
            updateNotification("خطا در دریافت GPS");
        }
    }

    @Override
    public void onLocationChanged(Location location) {

        if (location == null) {
            return;
        }

        float speedKmh = location.hasSpeed()
                ? location.getSpeed() * 3.6f
                : 0f;

        if (Float.isNaN(speedKmh)
                || Float.isInfinite(speedKmh)
                || speedKmh < 0f) {
            speedKmh = 0f;
        }

        // نرم‌سازی تغییرات سرعت GPS
        if (!hasSpeedSample) {
            smoothedSpeedKmh = speedKmh;
            hasSpeedSample = true;
        } else {
            smoothedSpeedKmh =
                    0.65f * smoothedSpeedKmh
                    + 0.35f * speedKmh;
        }

        int targetPercent =
                calculateTargetPercent(smoothedSpeedKmh);

        applyMediaVolumePercent(targetPercent);

        updateNotification(
                String.format(
                        java.util.Locale.getDefault(),
                        "سرعت: %.0f km/h | صدای هدف: %d%%",
                        smoothedSpeedKmh,
                        targetPercent
                )
        );
    }

    // محاسبه درصد صدا بر اساس سرعت
    private int calculateTargetPercent(float kmh) {

        // سرعت صفر تا ۱۰: صدای ۶۰ درصد
        if (kmh <= 10f) {
            return 60;
        }

        // سرعت ۷۵ به بالا: صدای ۱۰۰ درصد
        if (kmh >= 75f) {
            return 100;
        }

        // افزایش تدریجی از ۶۰ تا ۱۰۰ درصد
        return 60 + Math.round(
                (kmh - 10f) * 40f / 65f
        );
    }

    // تغییر صدای موسیقی و رسانه گوشی
    private void applyMediaVolumePercent(int percent) {

        if (audioManager == null) {
            return;
        }

        int boundedPercent =
                Math.max(0, Math.min(100, percent));

        if (lastAppliedPercent == boundedPercent) {
            return;
        }

        int maxVolume =
                audioManager.getStreamMaxVolume(
                        AudioManager.STREAM_MUSIC
                );

        if (maxVolume <= 0) {
            return;
        }

        int targetVolume = Math.round(
                (boundedPercent / 100f) * maxVolume
        );

        targetVolume =
                Math.max(0, Math.min(maxVolume, targetVolume));

        try {
            // فقط صدای موسیقی و رسانه تغییر می‌کند
            audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    targetVolume,
                    0
            );

            lastAppliedPercent = boundedPercent;

        } catch (SecurityException e) {
            Log.e(TAG, "Unable to change media volume", e);
        }
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "کنترل صدای وابسته به سرعت",
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "کنترل صدای موسیقی بر اساس سرعت GPS"
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {

        return new NotificationCompat.Builder(
                this,
                CHANNEL_ID
        )
                .setContentTitle("کنترل صدای خودرو")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify(
                    NOTIFICATION_ID,
                    buildNotification(text)
            );
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        // بدون نیاز به اقدام
    }

    @Override
    public void onProviderDisabled(String provider) {

        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            updateNotification(
                    "GPS خاموش است؛ برای کنترل صدا GPS را روشن کنید"
            );
        }
    }

    @Override
    public void onStatusChanged(
            String provider,
            int status,
            Bundle extras
    ) {
        // برای سازگاری با نسخه‌های مختلف اندروید
    }

    @Override
    public void onDestroy() {

        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ignored) {
                // مجوز ممکن است لغو شده باشد
            }
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
