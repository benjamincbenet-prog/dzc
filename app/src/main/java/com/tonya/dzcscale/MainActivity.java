package com.tonya.dzcscale;

import android.Manifest;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;

import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tonya.dzcscale.ble.DzcBleClient;
import com.tonya.dzcscale.body.BodyCompositionCalculator;
import com.tonya.dzcscale.health.HealthConnectBridge;
import com.tonya.dzcscale.history.MeasurementHistoryRepository;
import com.tonya.dzcscale.model.BodyMetrics;
import com.tonya.dzcscale.model.Measurement;
import com.tonya.dzcscale.model.Sex;

import java.util.Locale;
import java.util.Set;

/**
 * Main measurement screen and application workflow coordinator.
 * It validates the persistent profile, starts BLE collection, converts a raw
 * Measurement into BodyMetrics, presents results, and coordinates manual or
 * automatic Health Connect writes.
 */
public class MainActivity extends AppCompatActivity {
    public static final String ACTION_MEASURE = "com.tonya.dzcscale.action.MEASURE";
    public static final String ACTION_SHOW_LAST_RESULT = "com.tonya.dzcscale.action.SHOW_LAST_RESULT";
    private boolean measurementRequestedFromIntent;
    private TextView status, results, profileSummary, healthSummary;
    private Button connect, save;
    private BodyMetrics currentMetrics;
    private Measurement currentMeasurement;
    private DzcBleClient bleClient;
    private SharedPreferences prefs;
    private ActivityResultLauncher<Set<String>> healthPermissionLauncher;
    private boolean currentMeasurementSaved;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Targeting modern Android enables edge-to-edge content. We explicitly consume
        // system-bar insets below so the header never overlaps the status bar.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        applySystemBarInsets();

        prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        status = findViewById(R.id.status);
        results = findViewById(R.id.results);
        profileSummary = findViewById(R.id.profileSummary);
        healthSummary = findViewById(R.id.healthSummary);
        connect = findViewById(R.id.connect);
        save = findViewById(R.id.save);

        healthPermissionLauncher = registerForActivityResult(
                PermissionController.createRequestPermissionResultContract("com.google.android.apps.healthdata"),
                granted -> {
                    if (granted.containsAll(HealthConnectBridge.requiredPermissions())) {
                        writeToHealthConnect();
                    } else {
                        status.setText("Health Connect permissions were not fully granted");
                        Toast.makeText(this, "Grant the requested write permissions to sync", Toast.LENGTH_LONG).show();
                        if (currentMeasurement != null) save.setEnabled(true);
                    }
                }
        );

        findViewById(R.id.history).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        connect.setOnClickListener(v -> startMeasurement());
        save.setOnClickListener(v -> beginHealthConnectSave());
        updateProfileAndSyncSummary();
        handleMeasurementIntent(getIntent());
        if (prefs.getBoolean(SettingsActivity.KEY_AUTO_CONNECT, false) && !measurementRequestedFromIntent) {
            connect.postDelayed(this::startMeasurement, 500L);
        }
    }

    /** Accepts Quick Settings and NFC deep-link entry points without duplicating BLE logic. */
    private void handleMeasurementIntent(Intent intent) {
        if (intent == null) return;
        boolean requested = ACTION_MEASURE.equals(intent.getAction())
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())
                || Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null
                && "dzcscale".equalsIgnoreCase(intent.getData().getScheme());
        if (requested && !measurementRequestedFromIntent) {
            measurementRequestedFromIntent = true;
            connect.post(() -> { measurementRequestedFromIntent = false; startMeasurement(); });
        }
    }

    @Override
    protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleMeasurementIntent(intent); }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null) {
            updateProfileAndSyncSummary();
            if (prefs.getBoolean(SettingsActivity.KEY_BACKGROUND_DETECTION, false)) BackgroundScaleScanner.start(this);
        }
    }

    /**
     * Applies status/navigation bar insets to the scrolling container.
     * Keeping this in Java rather than relying on a fixed XML margin makes the
     * dashboard safe on notched, punch-hole, and edge-to-edge devices.
     */
    private void applySystemBarInsets() {
        View scroll = findViewById(R.id.mainScroll);
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

    /** Refreshes visible profile and sync-mode text from persistent preferences. */
    private void updateProfileAndSyncSummary() {
        String name = prefs.getString(SettingsActivity.KEY_NAME, "").trim();
        String height = prefs.getString(SettingsActivity.KEY_HEIGHT, "").trim();
        String age = prefs.getString(SettingsActivity.KEY_AGE, "").trim();
        String sex = prefs.getString(SettingsActivity.KEY_SEX, Sex.FEMALE.name());

        // Keep the dashboard greeting intentionally light; biological details belong
        // on the Profile screen where they can be reviewed and edited together.
        Button avatar = findViewById(R.id.settings);
        if (height.isEmpty() || age.isEmpty()) {
            profileSummary.setText("Set up your profile for personalized estimates");
            avatar.setText("P");
        } else {
            profileSummary.setText(name.isEmpty() ? "Your profile is ready" : "Hello, " + name);
            avatar.setText(profileInitials(name));
        }

        boolean autoSync = prefs.getBoolean(SettingsActivity.KEY_AUTO_SYNC, false);
        healthSummary.setText(autoSync
                ? "Auto-sync is on. Completed measurements will be sent automatically."
                : "Manual sync is on. Save each completed measurement when you are ready.");
    }



    /**
     * Produces a compact, stable avatar label from the saved name.
     * One-word names use their first letter; multi-word names use the first two initials.
     */
    private String profileInitials(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "P";
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase(Locale.US);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                .toUpperCase(Locale.US);
    }

    /** Ensures required formula inputs are present and inside the app-supported ranges. */
    private boolean hasValidProfile() {
        try {
            double h = Double.parseDouble(prefs.getString(SettingsActivity.KEY_HEIGHT, ""));
            int a = Integer.parseInt(prefs.getString(SettingsActivity.KEY_AGE, ""));
            return h >= 100 && h <= 250 && a >= 16 && a <= 100;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Validates profile/permissions and begins one complete BLE measurement session. */
    private void startMeasurement() {
        if (!MeasurementCoordinator.begin()) { status.setText("A measurement session is already active"); return; }
        if (MeasurementCoordinator.recentlyCompleted()) { MeasurementCoordinator.fail(); status.setText("Recent measurement protected from duplicate trigger"); return; }
        if (!hasValidProfile()) {
            Toast.makeText(this, "Please complete your profile before measuring", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            MeasurementCoordinator.fail();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 10);
            MeasurementCoordinator.fail();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            MeasurementCoordinator.fail();
            return;
        }

        final double h = Double.parseDouble(prefs.getString(SettingsActivity.KEY_HEIGHT, ""));
        final int a = Integer.parseInt(prefs.getString(SettingsActivity.KEY_AGE, ""));
        final String waistText = prefs.getString(SettingsActivity.KEY_WAIST, "").trim();
        final double waistCm;
        try { waistCm = waistText.isEmpty() ? Double.NaN : Double.parseDouble(waistText); }
        catch (Exception ignored) { status.setText("Profile waist value is invalid"); connect.setEnabled(true); MeasurementCoordinator.fail(); return; }

        final Sex selectedSex = Sex.MALE.name().equals(prefs.getString(SettingsActivity.KEY_SEX, Sex.FEMALE.name()))
                ? Sex.MALE : Sex.FEMALE;

        currentMeasurementSaved = false;
        connect.setEnabled(false);
        save.setEnabled(false);
        save.setText("Save to Health Connect");
        status.setText("Connecting to DZC scale…");
        results.setText("Waiting for a stable weight and impedance reading…");

        bleClient = new DzcBleClient(this, new DzcBleClient.Listener() {
            @Override
            public void status(String message) {
                runOnUiThread(() -> status.setText(message));
            }

            @Override
            public void error(String message) {
                runOnUiThread(() -> {
                    status.setText(message);
                    connect.setEnabled(true);
                    MeasurementCoordinator.fail();
                });
            }

            @Override
            public void measurement(Measurement measurement) {
                currentMeasurement = measurement;
                currentMetrics = BodyCompositionCalculator.calculate(measurement, h, a, selectedSex, waistCm);
                MeasurementHistoryRepository.add(MainActivity.this, measurement, currentMetrics);
                MeasurementCoordinator.complete();
                runOnUiThread(() -> {
                    status.setText("Measurement complete");
                    results.setText(format(currentMetrics, currentMeasurement));
                    connect.setEnabled(true);
                    save.setEnabled(true);
                    if (prefs.getBoolean(SettingsActivity.KEY_AUTO_SYNC, false)) {
                        beginHealthConnectSave();
                    }
                });
            }
        });
        bleClient.connect();
    }

    /** Checks Health Connect availability and permissions before starting a write. */
    private void beginHealthConnectSave() {
        if (currentMetrics == null || currentMeasurement == null || currentMeasurementSaved) return;
        int healthStatus = HealthConnectClient.getSdkStatus(this);
        if (healthStatus != HealthConnectClient.SDK_AVAILABLE) {
            String message = healthStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
                    ? "Health Connect needs to be installed or updated"
                    : "Health Connect is unavailable on this device/profile";
            status.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            save.setEnabled(true);
            return;
        }

        save.setEnabled(false);
        HealthConnectBridge.checkPermissions(this, new HealthConnectBridge.PermissionCallback() {
            @Override
            public void onResult(boolean granted) {
                if (granted) writeToHealthConnect();
                else healthPermissionLauncher.launch(HealthConnectBridge.requiredPermissions());
            }

            @Override
            public void onError(String message) {
                save.setEnabled(true);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Writes the current completed measurement and disables duplicate saves on success. */
    private void writeToHealthConnect() {
        save.setEnabled(false);
        status.setText("Saving to Health Connect…");
        HealthConnectBridge.write(this, currentMetrics, currentMeasurement.timeMillis(), new HealthConnectBridge.WriteCallback() {
            @Override
            public void onSuccess() {
                currentMeasurementSaved = true;
                status.setText("Saved to Health Connect");
                save.setText("Saved to Health Connect");
                save.setEnabled(false);
                Toast.makeText(MainActivity.this, "Measurement saved to Health Connect", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                status.setText("Health Connect save failed");
                save.setEnabled(true);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Formats measured values, estimates, and acquisition diagnostics for the result card. */
    private String format(BodyMetrics x, Measurement m) {
        String bodyFatDetail = x.waistEnhanced()
                ? String.format(Locale.US,
                "Body fat consensus  %.1f%%\nBIA estimate  %.1f%%   Waist estimate  %.1f%%\nWaist  %.1f cm   Waist/height  %.3f",
                x.bodyFatPercent(), x.biaBodyFatPercent(), x.waistBodyFatPercent(),
                x.waistCm(), x.waistToHeightRatio())
                : String.format(Locale.US,
                "Estimated body fat  %.1f%%\nBIA estimate  %.1f%%\nAdd waist in Profile to enable an independent anthropometric cross-check",
                x.bodyFatPercent(), x.biaBodyFatPercent());

        return String.format(Locale.US,
                "Weight  %.2f kg\nBMI  %.1f\nImpedance  %.1f Ω\nQuality  %d/100\n\n%s\n\n" +
                "Fat mass  %.1f kg\nLean body mass  %.1f kg\nEstimated body water  %.1f kg\nBMR  %.0f kcal/day\n\n" +
                "Model  %s\nAlgorithm  %s\n\n" +
                "Live samples  %d   BIA frames  %d\nImpedance samples  %d\n" +
                "Impedance SD  %.1f Ω   Weight SD  %.3f kg",
                x.weightKg(), x.bmi(), x.impedanceOhm(), x.qualityScore(), bodyFatDetail,
                x.fatMassKg(), x.leanMassKg(), x.bodyWaterKg(), x.bmrKcal(),
                x.primaryModel(), x.algorithmVersion(),
                m.sampleCount(), m.stabilizedSampleCount(), m.impedanceSampleCount(),
                m.impedanceStdDevOhm(), m.weightStdDevKg());
    }

    @Override
    protected void onDestroy() {
        if (bleClient != null) bleClient.close();
        super.onDestroy();
    }
}
