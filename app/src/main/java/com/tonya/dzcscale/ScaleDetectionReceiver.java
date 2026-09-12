package com.tonya.dzcscale;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/** Receives the explicit BLE PendingIntent match for the paired scale. */
public final class ScaleDetectionReceiver extends BroadcastReceiver {
    private static final String TAG = "ScaleDetectionReceiver";
    private static final String CHANNEL = "measurement";
    private static final int DETECTED_ID = 4203;

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !BackgroundScaleScanner.ACTION_SCALE_FOUND.equals(intent.getAction())) return;

        Log.i(TAG, "Paired scale detected by background BLE scan");
        if (!MeasurementCoordinator.begin() || MeasurementCoordinator.recentlyCompleted()) {
            Log.i(TAG, "Ignoring detection: measurement already active/recent");
            MeasurementCoordinator.fail();
            return;
        }

        Intent service = new Intent(context, BackgroundMeasurementService.class)
                .setAction(BackgroundMeasurementService.ACTION_BACKGROUND_MEASURE);
        try {
            ContextCompat.startForegroundService(context, service);
            Log.i(TAG, "Foreground measurement service start requested");
        } catch (android.app.ForegroundServiceStartNotAllowedException e) {
            Log.e(TAG, "Android blocked background foreground-service start", e);
            MeasurementCoordinator.fail();
            showTapToMeasure(context, "Scale detected — tap to start measurement");
        } catch (SecurityException e) {
            Log.e(TAG, "Bluetooth/foreground-service permission denied", e);
            MeasurementCoordinator.fail();
            showTapToMeasure(context, "Permission needed — tap to open DZC Scale");
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to start background measurement service", e);
            MeasurementCoordinator.fail();
            showTapToMeasure(context, "Scale detected — tap to start measurement");
        }
    }

    private void showTapToMeasure(Context context, String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Scale measurement", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Scale detection and measurement alerts");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, MainActivity.class)
                .setAction(MainActivity.ACTION_MEASURE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, DETECTED_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("DZC Scale detected")
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(DETECTED_ID, builder.build());
    }
}
