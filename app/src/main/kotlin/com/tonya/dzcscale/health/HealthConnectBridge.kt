package com.tonya.dzcscale.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import com.tonya.dzcscale.model.BodyMetrics
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId

/**
 * Small Java-callable boundary around Health Connect. Permission checks and writes
 * run off the UI thread, while callback results are returned on the main thread.
 */
object HealthConnectBridge {
    @JvmStatic
    /** Returns every write permission required by the records produced by this app. */
    fun requiredPermissions(): Set<String> = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(LeanBodyMassRecord::class),
        HealthPermission.getWritePermission(BodyWaterMassRecord::class),
        HealthPermission.getWritePermission(BasalMetabolicRateRecord::class)
    )

    @JvmStatic
    /** Reports whether a usable Health Connect provider is available. */
    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    @JvmStatic
    /** Reads granted permissions asynchronously without reading any health records. */
    fun checkPermissions(context: Context, callback: PermissionCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                withContext(Dispatchers.Main) { callback.onResult(granted.containsAll(requiredPermissions())) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { callback.onError(t.message ?: "Unable to check Health Connect permissions") }
            }
        }
    }

    @JvmStatic
    /** Converts BodyMetrics into timestamped Health Connect records and inserts them. */
    fun write(context: Context, metrics: BodyMetrics, timeMillis: Long, callback: WriteCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val device = Device(Device.TYPE_UNKNOWN, "Bear Electric Appliance", "DZC-D18E3")
                val metadata = Metadata.activelyRecorded(device)
                val time = Instant.ofEpochMilli(timeMillis)
                val offset = ZoneId.systemDefault().rules.getOffset(time)

                val records = listOf(
                    WeightRecord(time, offset, Mass.kilograms(metrics.weightKg()), metadata),
                    BodyFatRecord(time, offset, Percentage(metrics.bodyFatPercent()), metadata),
                    LeanBodyMassRecord(time, offset, Mass.kilograms(metrics.leanMassKg()), metadata),
                    BodyWaterMassRecord(time, offset, Mass.kilograms(metrics.bodyWaterKg()), metadata),
                    BasalMetabolicRateRecord(time, offset, Power.kilocaloriesPerDay(metrics.bmrKcal()), metadata)
                )
                client.insertRecords(records)
                withContext(Dispatchers.Main) { callback.onSuccess() }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { callback.onError(t.message ?: "Health Connect write failed") }
            }
        }
    }

    interface PermissionCallback {
        fun onResult(granted: Boolean)
        fun onError(message: String)
    }

    interface WriteCallback {
        fun onSuccess()
        fun onError(message: String)
    }
}
