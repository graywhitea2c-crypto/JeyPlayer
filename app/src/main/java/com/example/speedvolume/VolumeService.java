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

public class VolumeService extends Service implements LocationListener {
    private static final String CHANNEL = "speed_volume_channel";
    private LocationManager locationManager;
    private AudioManager audioManager;
    private int originalVolume = -1;

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        createChannel();
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL)
            : new Notification.Builder(this);
        Notification notification = builder.setContentTitle("کنترل هوشمند صدا")
            .setContentText("تنظیم صدای رسانه با سرعت GPS فعال است")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true).build();
        startForeground(7, notification);
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) onLocationChanged(last);
        } catch (SecurityException ignored) { stopSelf(); }
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null || !location.hasSpeed()) return;
        float kmh = location.getSpeed() * 3.6f;
        int targetPercent;
        if (kmh < 20f) targetPercent = 20;
        else if (kmh >= 80f) targetPercent = 100;
        else targetPercent = 20 + Math.round((kmh - 20f) * 80f / 60f);

        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int target = Math.round(max * targetPercent / 100f);
        // Android media volume is discrete steps, not a continuous percentage.
        try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0); }
        catch (SecurityException ignored) { }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "کنترل هوشمند صدا", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        if (locationManager != null) locationManager.removeUpdates(this);
        // Leave the last applied volume in place; do not unexpectedly restore or change it.
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
}
