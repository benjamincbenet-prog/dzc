package com.tonya.dzcscale.ble;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.SharedPreferences;
import com.tonya.dzcscale.SettingsActivity;
import android.os.Handler;
import android.os.Looper;

import com.tonya.dzcscale.model.Measurement;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

/**
 * BLE transport and measurement state machine for the Bear/Xiong DZC-D18E3.
 * Scanning, GATT subscription, packet classification, sample retention,
 * timeout handling, and final acquisition diagnostics live here so the UI
 * does not need to understand the scale protocol.
 */
public final class DzcBleClient {
    public interface Listener { void status(String s); void measurement(Measurement m); void error(String s); }

    // Packet classes observed in the nRF Connect capture.
    private static final int MODE_LIVE_WEIGHT = 0x01;
    private static final int MODE_BIA_RESULT = 0xA0;

    private static final int MAX_SAMPLES = 30;
    private static final long BIA_CONFIRMATION_WINDOW_MS = 1800L;
    private static final long MEASUREMENT_TIMEOUT_MS = 25000L;

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothLeScanner scanner;
    private boolean connecting;
    private boolean finalized;
    private boolean subscribed;
    private final SharedPreferences prefs;

    private final ArrayList<WeightSample> weightSamples = new ArrayList<>();
    private final ArrayList<BiaSample> biaSamples = new ArrayList<>();

    private final UUID service = uuid(0xFFF0);
    private final UUID notify = uuid(0xFFF4);
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Runnable finalizeRunnable = this::finalizeMeasurement;
    private final Runnable timeoutRunnable = this::measurementTimedOut;

    private static final class WeightSample {
        final double weightKg;
        final int mode;
        final long timeMillis;
        WeightSample(double weightKg, int mode, long timeMillis) {
            this.weightKg = weightKg;
            this.mode = mode;
            this.timeMillis = timeMillis;
        }
    }

    private static final class BiaSample {
        final double weightKg;
        final double impedanceOhm;
        final long timeMillis;
        BiaSample(double weightKg, double impedanceOhm, long timeMillis) {
            this.weightKg = weightKg;
            this.impedanceOhm = impedanceOhm;
            this.timeMillis = timeMillis;
        }
    }

    public DzcBleClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.prefs = this.context.getSharedPreferences(SettingsActivity.PREFS, Context.MODE_PRIVATE);
    }

    /** Starts a fresh scan and resets all transient samples from the prior attempt. */
    public void connect() {
        resetMeasurementState();
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        if (manager == null || manager.getAdapter() == null || !manager.getAdapter().isEnabled()) {
            listener.error("Bluetooth is unavailable or disabled");
            return;
        }
        scanner = manager.getAdapter().getBluetoothLeScanner();
        if (scanner == null) { listener.error("BLE scanning unavailable"); return; }
        listener.status("Scanning for DZC scale...");
        scanner.startScan(scanCallback);
    }

    /** Cancels timers and releases scanner/GATT resources; safe during lifecycle teardown. */
    public void close() {
        handler.removeCallbacksAndMessages(null);
        if (scanner != null) try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
        if (gatt != null) { gatt.disconnect(); gatt.close(); gatt = null; }
        connecting = false;
        subscribed = false;
    }

    private void resetMeasurementState() {
        handler.removeCallbacks(finalizeRunnable);
        handler.removeCallbacks(timeoutRunnable);
        weightSamples.clear();
        biaSamples.clear();
        finalized = false;
        subscribed = false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            if (connecting) return;
            String name = result.getDevice().getName();
            String knownAddress = prefs.getString(SettingsActivity.KEY_SCALE_ADDRESS, "");
            boolean known = !knownAddress.isEmpty() && knownAddress.equalsIgnoreCase(result.getDevice().getAddress());
            boolean fallback = knownAddress.isEmpty() && name != null && name.toUpperCase(Locale.US).startsWith("DZC");
            if (known || fallback) {
                connecting = true;
                try { scanner.stopScan(this); } catch (Exception ignored) {}
                listener.status("Connecting to " + name + "...");
                gatt = result.getDevice().connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            }
        }
        @Override public void onScanFailed(int errorCode) { listener.error("BLE scan failed: " + errorCode); }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.error("GATT connection error: " + status);
                safeClose(g);
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.status("Discovering scale services...");
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (!finalized && !biaSamples.isEmpty()) finalizeMeasurement();
                if (!finalized) listener.status("Disconnected");
                safeClose(g);
                connecting = false;
                subscribed = false;
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) { listener.error("Service discovery failed"); return; }
            BluetoothGattService scaleService = g.getService(service);
            if (scaleService == null) { listener.error("DZC FFF0 service not found"); return; }
            BluetoothGattCharacteristic characteristic = scaleService.getCharacteristic(notify);
            if (characteristic == null) { listener.error("DZC FFF4 characteristic not found"); return; }
            if (!g.setCharacteristicNotification(characteristic, true)) { listener.error("Unable to enable FFF4 notifications"); return; }
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD);
            if (descriptor == null) { listener.error("Notification descriptor not found"); return; }
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!g.writeDescriptor(descriptor)) { listener.error("Unable to subscribe to scale notifications"); return; }
            subscribed = true;
            listener.status("Step on scale and remain still...");
            handler.postDelayed(timeoutRunnable, MEASUREMENT_TIMEOUT_MS);
        }

        @Override @SuppressWarnings("deprecation")
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
            handlePacket(characteristic.getValue());
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic, byte[] value) {
            handlePacket(value);
        }
    };

    /** Validates a notification, classifies its observed mode, and updates the proper sample buffer. */
    private void handlePacket(byte[] value) {
        DzcPacketParser.Packet packet = DzcPacketParser.parse(value);
        if (packet == null || finalized || !subscribed) return;
        if (!plausibleWeight(packet.weightKg())) return;

        long now = System.currentTimeMillis();
        int mode = packet.modeFlags();

        if (mode == MODE_LIVE_WEIGHT) {
            addWeight(packet.weightKg(), mode, now);
            listener.status("Measuring... " + formatKg(packet.weightKg())
                    + " (" + weightSamples.size() + " live samples)");
            return;
        }

        if (mode == MODE_BIA_RESULT) {
            if (!plausibleImpedance(packet.impedanceOhm())) return;
            addWeight(packet.weightKg(), mode, now);
            addBia(packet.weightKg(), packet.impedanceOhm(), now);
            listener.status("BIA result received: " + formatKg(packet.weightKg())
                    + ", " + String.format(Locale.US, "%.1f Ω", packet.impedanceOhm())
                    + ". Confirming...");

            // Every additional A0 frame extends the confirmation window. This
            // captures repeated final frames without disconnecting after one.
            handler.removeCallbacks(finalizeRunnable);
            handler.postDelayed(finalizeRunnable, BIA_CONFIRMATION_WINDOW_MS);
            return;
        }

        // Keep unknown protocol classes visible without contaminating samples.
        listener.status("Waiting for measurement (DZC mode 0x"
                + String.format(Locale.US, "%02X", mode) + ")...");
    }

    private void addWeight(double weightKg, int mode, long now) {
        weightSamples.add(new WeightSample(weightKg, mode, now));
        while (weightSamples.size() > MAX_SAMPLES) weightSamples.remove(0);
    }

    private void addBia(double weightKg, double impedanceOhm, long now) {
        biaSamples.add(new BiaSample(weightKg, impedanceOhm, now));
        while (biaSamples.size() > MAX_SAMPLES) biaSamples.remove(0);
    }

    private void measurementTimedOut() {
        if (finalized) return;
        if (!biaSamples.isEmpty()) {
            finalizeMeasurement();
        } else {
            listener.error("No BIA result received. Keep both feet on the scale and try again.");
            if (gatt != null) gatt.disconnect();
        }
    }

    /** Selects final values, computes diagnostics, emits one Measurement, then disconnects. */
    private void finalizeMeasurement() {
        if (finalized || biaSamples.isEmpty()) return;
        finalized = true;
        handler.removeCallbacks(finalizeRunnable);
        handler.removeCallbacks(timeoutRunnable);

        ArrayList<Double> biaWeights = new ArrayList<>();
        ArrayList<Double> impedances = new ArrayList<>();
        ArrayList<Double> liveWeights = new ArrayList<>();
        for (BiaSample s : biaSamples) {
            biaWeights.add(s.weightKg);
            impedances.add(s.impedanceOhm);
        }
        for (WeightSample s : weightSamples) {
            if (s.mode == MODE_LIVE_WEIGHT) liveWeights.add(s.weightKg);
        }

        // The BIA frame is the final body-composition frame, so its weight is
        // preferred. Live frames remain useful for stability diagnostics only.
        double weight = median(biaWeights);
        double impedance = median(impedances);
        double impedanceSd = standardDeviation(impedances);
        double weightSd = standardDeviation(biaWeights.size() >= 2 ? biaWeights : liveWeights);
        int quality = qualityScore(weightSamples.size(), biaSamples.size(), impedanceSd, weightSd);

        Measurement measurement = new Measurement(
                weight,
                impedance,
                System.currentTimeMillis(),
                weightSamples.size(),
                biaWeights.size(),
                biaSamples.size(),
                impedanceSd,
                weightSd,
                quality
        );
        listener.measurement(measurement);
        if (gatt != null) gatt.disconnect();
    }

    private static boolean plausibleWeight(double weightKg) {
        return weightKg >= 20.0 && weightKg <= 300.0;
    }

    private static boolean plausibleImpedance(double impedanceOhm) {
        return impedanceOhm >= 100.0 && impedanceOhm <= 2000.0;
    }

    private static String formatKg(double kg) {
        return String.format(Locale.US, "%.1f kg", kg);
    }

    private static double median(ArrayList<Double> values) {
        if (values.isEmpty()) return 0;
        ArrayList<Double> copy = new ArrayList<>(values);
        copy.sort(Double::compareTo);
        int mid = copy.size() / 2;
        return copy.size() % 2 == 0 ? (copy.get(mid - 1) + copy.get(mid)) / 2.0 : copy.get(mid);
    }

    private static double standardDeviation(ArrayList<Double> values) {
        if (values.size() < 2) return 0;
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.size();
        double sum = 0;
        for (double v : values) sum += (v - mean) * (v - mean);
        return Math.sqrt(sum / values.size());
    }

    /**
     * Scores acquisition quality only. It does not claim medical or formula accuracy.
     * The score rewards complete protocol data and stability while clamping to 0..100.
     */
    private static int qualityScore(int weightCount, int biaCount, double impedanceSd, double weightSd) {
        int score = 45;                         // valid protocol-complete measurement
        score += Math.min(20, weightCount * 2); // stable live stream helps confidence
        score += Math.min(20, biaCount * 10);   // repeated final BIA frames are strongest
        if (impedanceSd <= 2.0) score += 10;
        else if (impedanceSd <= 5.0) score += 7;
        else if (impedanceSd <= 10.0) score += 3;
        if (weightSd <= 0.05) score += 5;
        else if (weightSd <= 0.15) score += 3;
        else if (weightSd > 0.50) score -= 10;
        return Math.max(0, Math.min(100, score));
    }

    private void safeClose(BluetoothGatt g) {
        try { g.close(); } catch (Exception ignored) {}
        if (g == gatt) gatt = null;
    }

    private static UUID uuid(int x) {
        return UUID.fromString(String.format(Locale.US, "0000%04x-0000-1000-8000-00805f9b34fb", x));
    }
}
