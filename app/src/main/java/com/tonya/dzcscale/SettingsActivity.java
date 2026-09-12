package com.tonya.dzcscale;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.tonya.dzcscale.model.Sex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Profile and application settings screen. Values are persisted locally in
 * SharedPreferences so formula inputs and sync preferences survive restarts.
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private static final int REQUEST_PAIR_BLUETOOTH = 21;

    public static final String PREFS = "user_profile";
    public static final String KEY_NAME = "name";
    public static final String KEY_HEIGHT = "height";
    public static final String KEY_AGE = "age";
    public static final String KEY_SEX = "sex";
    public static final String KEY_AUTO_SYNC = "auto_sync";
    public static final String KEY_WAIST = "waist";
    public static final String KEY_SCALE_NAME = "scale_name";
    public static final String KEY_SCALE_ADDRESS = "scale_address";
    public static final String KEY_AUTO_CONNECT = "auto_connect";
    public static final String KEY_BACKGROUND_DETECTION = "background_detection";

    private SharedPreferences prefs;
    private EditText name, height, age, waist;
    private RadioGroup sex;
    private MaterialSwitch autoSync, autoConnect, backgroundDetection;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private BluetoothLeScanner activeScanner;
    private ScanCallback activeScanCallback;
    private boolean pairingInProgress;
    private boolean restartBackgroundDetectionAfterPair;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_settings);
        applySystemBarInsets();

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        name = findViewById(R.id.name);
        height = findViewById(R.id.height);
        age = findViewById(R.id.age);
        waist = findViewById(R.id.waist);
        sex = findViewById(R.id.sex);
        autoSync = findViewById(R.id.autoSync);
        autoConnect = findViewById(R.id.autoConnect);
        backgroundDetection = findViewById(R.id.backgroundDetection);
        findViewById(R.id.pairScale).setOnClickListener(v -> pairScale());

        load();
        findViewById(R.id.saveProfile).setOnClickListener(v -> save());
    }

    private void applySystemBarInsets() {
        View scroll = findViewById(R.id.settingsScroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(view.getPaddingLeft(), bars.top,
                    view.getPaddingRight(), bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(scroll);
    }

    private void load() {
        name.setText(prefs.getString(KEY_NAME, ""));
        height.setText(prefs.getString(KEY_HEIGHT, ""));
        age.setText(prefs.getString(KEY_AGE, ""));
        waist.setText(prefs.getString(KEY_WAIST, ""));
        String savedSex = prefs.getString(KEY_SEX, Sex.FEMALE.name());
        sex.check(Sex.MALE.name().equals(savedSex) ? R.id.male : R.id.female);
        autoSync.setChecked(prefs.getBoolean(KEY_AUTO_SYNC, false));
        autoConnect.setChecked(prefs.getBoolean(KEY_AUTO_CONNECT, false));
        backgroundDetection.setChecked(prefs.getBoolean(KEY_BACKGROUND_DETECTION, false));
        updateScaleSummary();
    }

    private void save() {
        String heightText = height.getText().toString().trim();
        String ageText = age.getText().toString().trim();
        String waistText = waist.getText().toString().trim();
        try {
            double h = Double.parseDouble(heightText);
            int a = Integer.parseInt(ageText);
            if (h < 100 || h > 250 || a < 16 || a > 100) throw new IllegalArgumentException();
            if (!waistText.isEmpty()) {
                double w = Double.parseDouble(waistText);
                double ratio = w / h;
                if (w < 45 || w > 200 || ratio < 0.25 || ratio > 0.90) throw new IllegalArgumentException();
            }

            Sex selectedSex = sex.getCheckedRadioButtonId() == R.id.male ? Sex.MALE : Sex.FEMALE;
            boolean backgroundEnabled = backgroundDetection.isChecked();
            prefs.edit()
                    .putString(KEY_NAME, name.getText().toString().trim())
                    .putString(KEY_HEIGHT, heightText)
                    .putString(KEY_AGE, ageText)
                    .putString(KEY_SEX, selectedSex.name())
                    .putString(KEY_WAIST, waistText)
                    .putBoolean(KEY_AUTO_SYNC, autoSync.isChecked())
                    .putBoolean(KEY_AUTO_CONNECT, autoConnect.isChecked())
                    .putBoolean(KEY_BACKGROUND_DETECTION, backgroundEnabled)
                    .apply();

            if (backgroundEnabled) {
                if (!BackgroundScaleScanner.start(this)) {
                    Toast.makeText(this,
                            "Background detection needs Bluetooth permission and a paired scale",
                            Toast.LENGTH_LONG).show();
                }
            } else {
                BackgroundScaleScanner.stop(this);
            }

            Toast.makeText(this, "Profile and settings saved", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception ignored) {
            Toast.makeText(this,
                    "Enter valid height/age values. Waist is optional; use 45–200 cm",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void updateScaleSummary() {
        android.widget.TextView v = findViewById(R.id.scaleSummary);
        String n = prefs.getString(KEY_SCALE_NAME, "");
        String a = prefs.getString(KEY_SCALE_ADDRESS, "");
        v.setText(n.isEmpty()
                ? "No scale selected. Pair your DZC-D18E3 for safer auto-connect."
                : n + (a.isEmpty() ? "" : "\n" + a));
    }

    /**
     * Starts a manual foreground BLE scan. Background detection is paused for the
     * duration so the two scan registrations cannot race with each other.
     */
    private void pairScale() {
        Log.i(TAG, "Pair Scale pressed");
        if (pairingInProgress) {
            Toast.makeText(this, "Already scanning for scales…", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean scanGranted = checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
            boolean connectGranted = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
            if (!scanGranted || !connectGranted) {
                Log.i(TAG, "Requesting Bluetooth scan/connect permissions for pairing");
                requestPermissions(
                        new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                        },
                        REQUEST_PAIR_BLUETOOTH
                );
                return;
            }
        }

        startPairingScan();
    }

    private void startPairingScan() {
        if (pairingInProgress) return;

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth is unavailable on this device", Toast.LENGTH_LONG).show();
            return;
        }
        if (!adapter.isEnabled()) {
            Toast.makeText(this, "Please turn on Bluetooth and try again", Toast.LENGTH_LONG).show();
            return;
        }

        BluetoothLeScanner scanner;
        try {
            scanner = adapter.getBluetoothLeScanner();
        } catch (SecurityException e) {
            Log.e(TAG, "Unable to obtain BLE scanner", e);
            Toast.makeText(this, "Bluetooth permission is required to pair the scale", Toast.LENGTH_LONG).show();
            return;
        }
        if (scanner == null) {
            Toast.makeText(this, "Bluetooth scanning unavailable", Toast.LENGTH_LONG).show();
            return;
        }

        if (prefs.getBoolean(KEY_BACKGROUND_DETECTION, false)) {
            restartBackgroundDetectionAfterPair = true;
            BackgroundScaleScanner.stop(this);
        } else {
            restartBackgroundDetectionAfterPair = false;
        }

        final Map<String, BluetoothDevice> devicesByAddress = new LinkedHashMap<>();
        final ArrayList<String> labels = new ArrayList<>();
        final ArrayList<BluetoothDevice> devices = new ArrayList<>();
        pairingInProgress = true;
        activeScanner = scanner;

        activeScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                String name;
                String address;
                try {
                    name = device.getName();
                    address = device.getAddress();
                } catch (SecurityException e) {
                    Log.e(TAG, "Bluetooth permission error while reading scan result", e);
                    return;
                }

                if (address == null || address.isEmpty()) return;
                String normalizedName = name == null ? "" : name.trim();
                if (normalizedName.toUpperCase(Locale.US).startsWith("DZC")) {
                    if (!devicesByAddress.containsKey(address)) {
                        devicesByAddress.put(address, device);
                        labels.add(normalizedName.isEmpty()
                                ? "DZC scale\n" + address
                                : normalizedName + "\n" + address);
                        devices.add(device);
                        Log.i(TAG, "Found DZC scale: " + normalizedName + " / " + address);
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "Manual BLE scan failed: " + errorCode);
                stopPairingScan();
                Toast.makeText(SettingsActivity.this,
                        "Scale scan failed (Bluetooth error " + errorCode + ")",
                        Toast.LENGTH_LONG).show();
            }
        };

        try {
            scanner.startScan(activeScanCallback);
            Log.i(TAG, "Manual BLE scale scan started");
            Toast.makeText(this, "Scanning for DZC scales…", Toast.LENGTH_SHORT).show();

            handler.postDelayed(() -> {
                if (!pairingInProgress) return;
                stopPairingScan();

                if (devices.isEmpty()) {
                    Toast.makeText(SettingsActivity.this,
                            "No DZC scale found",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Select your scale")
                        .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                            if (which < 0 || which >= devices.size()) return;
                            BluetoothDevice device = devices.get(which);
                            try {
                                String deviceName = device.getName();
                                String deviceAddress = device.getAddress();
                                prefs.edit()
                                        .putString(KEY_SCALE_NAME,
                                                deviceName == null || deviceName.isEmpty()
                                                        ? "DZC scale"
                                                        : deviceName)
                                        .putString(KEY_SCALE_ADDRESS, deviceAddress)
                                        .apply();
                                updateScaleSummary();
                                Log.i(TAG, "Paired scale saved: " + deviceAddress);
                                Toast.makeText(SettingsActivity.this,
                                        "Scale paired",
                                        Toast.LENGTH_SHORT).show();
                                if (prefs.getBoolean(KEY_BACKGROUND_DETECTION, false)) {
                                    BackgroundScaleScanner.start(SettingsActivity.this);
                                }
                            } catch (SecurityException e) {
                                Log.e(TAG, "Unable to save selected Bluetooth device", e);
                                Toast.makeText(SettingsActivity.this,
                                        "Bluetooth permission is required to save the scale",
                                        Toast.LENGTH_LONG).show();
                            }
                        })
                        .show();
            }, 5000L);
        } catch (SecurityException e) {
            Log.e(TAG, "Unable to start manual BLE scan", e);
            stopPairingScan();
            Toast.makeText(this,
                    "Bluetooth permission is required to scan for the scale",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void stopPairingScan() {
        handler.removeCallbacksAndMessages(null);
        if (activeScanner != null && activeScanCallback != null) {
            try {
                activeScanner.stopScan(activeScanCallback);
            } catch (Exception e) {
                Log.w(TAG, "Error stopping manual BLE scan", e);
            }
        }
        activeScanner = null;
        activeScanCallback = null;
        pairingInProgress = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PAIR_BLUETOOTH) return;

        boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);

        Log.i(TAG, "Pairing Bluetooth permissions result: " + granted);
        if (granted) {
            startPairingScan();
        } else {
            Toast.makeText(this,
                    "Bluetooth scan and connect permissions are required to pair the scale",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        stopPairingScan();
        if (restartBackgroundDetectionAfterPair
                && prefs != null
                && prefs.getBoolean(KEY_BACKGROUND_DETECTION, false)) {
            BackgroundScaleScanner.start(this);
        }
        super.onDestroy();
    }
}
