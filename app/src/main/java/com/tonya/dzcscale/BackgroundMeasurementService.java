package com.tonya.dzcscale;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.tonya.dzcscale.ble.DzcBleClient;
import com.tonya.dzcscale.body.BodyCompositionCalculator;
import com.tonya.dzcscale.history.MeasurementHistoryRepository;
import com.tonya.dzcscale.model.BodyMetrics;
import com.tonya.dzcscale.model.Measurement;
import com.tonya.dzcscale.model.Sex;

/** Runs a short foreground measurement session after the paired scale is detected. */
public final class BackgroundMeasurementService extends Service {
    public static final String ACTION_BACKGROUND_MEASURE = "com.tonya.dzcscale.action.BACKGROUND_MEASURE";
    private static final String CHANNEL = "measurement";
    private static final int FOREGROUND_ID = 4201;
    private static final int RESULT_ID = 4202;

    private DzcBleClient client;
    private SharedPreferences prefs;
    private boolean sessionFinished = false;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Log.i("BackgroundMeasurement", "Service started: " + (intent == null ? "null" : intent.getAction()));
            startAsForeground();
            startMeasurement();
        } catch (Exception e) {
            e.printStackTrace();
            MeasurementCoordinator.fail();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        Notification notification = buildNotification("DZC Scale Health", "Scale detected — preparing measurement...", true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
    }

    private void startMeasurement() {
        try {
            double height = Double.parseDouble(prefs.getString(SettingsActivity.KEY_HEIGHT, ""));
            int age = Integer.parseInt(prefs.getString(SettingsActivity.KEY_AGE, ""));
            String waistText = prefs.getString(SettingsActivity.KEY_WAIST, "").trim();
            double waist = waistText.isEmpty() ? Double.NaN : Double.parseDouble(waistText);
            Sex sex = Sex.MALE.name().equals(prefs.getString(SettingsActivity.KEY_SEX, Sex.FEMALE.name())) ? Sex.MALE : Sex.FEMALE;

            client = new DzcBleClient(this, new DzcBleClient.Listener() {
                @Override public void status(String message) { updateForegroundNotification(message); }
                @Override public void error(String message) { finishSession("Measurement not completed", message); }
                @Override public void measurement(Measurement measurement) {
                    if (sessionFinished) return;
                    sessionFinished = true;
                    try {
                        BodyMetrics metrics = BodyCompositionCalculator.calculate(measurement, height, age, sex, waist);
                        MeasurementHistoryRepository.add(BackgroundMeasurementService.this, measurement, metrics);
                        MeasurementCoordinator.complete();
                        showCompletionNotification(metrics);
                    } catch (Exception e) {
                        e.printStackTrace();
                        MeasurementCoordinator.fail();
                        showSimpleNotification("Measurement error", "Unable to save the measurement");
                    }
                    stopSelf();
                }
            });
            client.connect();
        } catch (Exception e) {
            e.printStackTrace();
            finishSession("Profile needs attention", "Open DZC Scale Health and complete your profile");
        }
    }

    private void updateForegroundNotification(String message) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(FOREGROUND_ID, buildNotification("DZC Scale Health", message, true));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showCompletionNotification(BodyMetrics metrics) {
        try {
            Intent viewIntent = new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_SHOW_LAST_RESULT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, RESULT_ID, viewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String summary = String.format(java.util.Locale.US, "%.2f kg • %.1f%% body fat", metrics.weightKg(), metrics.bodyFatPercent());
            String details = String.format(java.util.Locale.US,
                    "Weight %.2f kg\nBody fat %.1f%%\nBMI %.1f\nQuality %d/100",
                    metrics.weightKg(), metrics.bodyFatPercent(), metrics.bmi(), metrics.qualityScore());
            Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentTitle("Measurement complete").setContentText(summary)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(details))
                    .setContentIntent(pendingIntent).setAutoCancel(true).build();
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(RESULT_ID, notification);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showSimpleNotification(String title, String text) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(RESULT_ID, buildNotification(title, text, false));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Notification buildNotification(String title, String text, boolean ongoing) {
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle(title).setContentText(text).setOngoing(ongoing).setOnlyAlertOnce(true).build();
    }

    private void finishSession(String title, String text) {
        if (sessionFinished) return;
        sessionFinished = true;
        MeasurementCoordinator.fail();
        showSimpleNotification(title, text);
        stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Scale measurement", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows automated scale measurement progress");
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        if (client != null) try { client.close(); } catch (Exception e) { e.printStackTrace(); }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
