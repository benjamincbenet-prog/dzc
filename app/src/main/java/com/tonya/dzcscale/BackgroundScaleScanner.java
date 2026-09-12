package com.tonya.dzcscale;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/** Registers Android's low-power PendingIntent BLE scan for the user's paired scale. */
public final class BackgroundScaleScanner {
    public static final String ACTION_SCALE_FOUND = "com.tonya.dzcscale.action.SCALE_FOUND";
    private BackgroundScaleScanner() {}

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, ScaleDetectionReceiver.class).setAction(ACTION_SCALE_FOUND);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(context, 2001, intent, flags);
    }

    public static boolean start(Context context) {
        try {
            BluetoothManager manager = context.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            BluetoothLeScanner scanner = adapter == null ? null : adapter.getBluetoothLeScanner();
            if (scanner == null) return false;
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
            List<ScanFilter> filters = new ArrayList<>();
            // Address filtering is exact and battery-friendly. If no paired address exists,
            // do not start a broad background scan; pairing is required for this feature.
            String address = context.getSharedPreferences(SettingsActivity.PREFS, Context.MODE_PRIVATE)
                    .getString(SettingsActivity.KEY_SCALE_ADDRESS, "");
            if (address.isEmpty()) { Log.w("BackgroundScaleScanner", "No paired scale address"); return false; }
            filters.add(new ScanFilter.Builder().setDeviceAddress(address).build());
            scanner.startScan(filters, settings, pendingIntent(context));
            Log.i("BackgroundScaleScanner", "Background BLE scan registered for " + address);
            return true;
        } catch (SecurityException e) { e.printStackTrace(); return false; }
    }

    public static void stop(Context context) {
        try {
            BluetoothManager manager = context.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            BluetoothLeScanner scanner = adapter == null ? null : adapter.getBluetoothLeScanner();
            if (scanner != null) scanner.stopScan(pendingIntent(context));
        } catch (Exception ignored) { }
    }
}
