package software.rdd.apexperformance.core.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.model.Training
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

// Health Connect on Android, as Apple Health on the iPhone (AppleHealth.swift).
// Mi Fitness (Xiaomi), Google Fit, Fitbit, Samsung Health... write into it, so
// one connection covers them. Built into Android 14+, an app from Google Play before.
//
// - Heart rate during a training: read between the start and end the coach
//   marked; only the lowest, highest and average go to the server.
// - The client's day on home (sleep, steps, active energy, resting heart rate):
//   read on the phone and shown only to the client, never sent to the server.
//
// Only read while the app is open, nothing in the background.
object HealthConnect {
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

    val heartRatePermission = HealthPermission.getReadPermission(HeartRateRecord::class)
    private val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
    private val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val activeEnergyPermission = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    private val restingHeartRatePermission = HealthPermission.getReadPermission(RestingHeartRateRecord::class)

    // Everything the app reads (the manifest declares the same).
    val dailyPermissions: Set<String> = setOf(
        heartRatePermission, sleepPermission, stepsPermission, activeEnergyPermission, restingHeartRatePermission
    )

    enum class Availability { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, UNAVAILABLE }

    // Health Connect works from Android 9; before Android 14 it's an app from Google Play.
    fun availability(context: Context = ApexApp.appContext): Availability =
        when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_INSTALL_OR_UPDATE
            else -> Availability.UNAVAILABLE
        }

    val isAvailable: Boolean get() = availability() == Availability.AVAILABLE

    private val client: HealthConnectClient get() = HealthConnectClient.getOrCreate(ApexApp.appContext)

    // Health Connect's screen asking for the permissions; returns the granted ones.
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract(PROVIDER_PACKAGE)

    suspend fun grantedPermissions(): Set<String> =
        if (!isAvailable) emptySet()
        else try {
            client.permissionController.getGrantedPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }

    // Reading heart rate during trainings is allowed.
    suspend fun isConnected(): Boolean = heartRatePermission in grantedPermissions()

    // Reading the day (any of sleep, steps, energy, resting heart rate) is allowed.
    suspend fun isDailyConnected(): Boolean =
        grantedPermissions().any { it in dailyPermissions && it != heartRatePermission }

    // Health Connect on Google Play, to install or update it.
    fun openStore(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER_PACKAGE")))
        }
    }

    // Health Connect itself (its data and app permissions), like the Health app on the iPhone.
    fun openSettings(context: Context) {
        open(context, Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
    }

    private fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Nothing to open it with.
        }
    }

    // MARK: - The client's day

    // Sleep last night, steps and active energy today, latest resting heart rate.
    data class DailySummary(
        val sleepMinutes: Int? = null,
        val steps: Int? = null,
        val activeEnergyKilocalories: Int? = null,
        val restingHeartRate: Int? = null,
        val updatedAt: Instant = Instant.now()
    ) {
        val isEmpty: Boolean
            get() = sleepMinutes == null && steps == null && activeEnergyKilocalories == null && restingHeartRate == null
    }

    // Each value read on its own: one not allowed or failing leaves the others.
    suspend fun dailySummary(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): DailySummary =
        coroutineScope {
            val today = HealthMath.dayStart(now, zone)
            val sleep = async { orNull { sleepMinutes(HealthMath.sleepWindowStart(now, zone), now) } }
            val steps = async {
                orNull { aggregate(StepsRecord.COUNT_TOTAL, today, now)?.toInt() }
            }
            val energy = async {
                orNull { aggregate(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, today, now)?.inKilocalories?.roundToInt() }
            }
            val resting = async {
                orNull { latestRestingHeartRate(now.minus(Duration.ofDays(HealthMath.RESTING_HEART_RATE_DAYS)), now) }
            }
            DailySummary(sleep.await(), steps.await(), energy.await(), resting.await(), now)
        }

    // Asleep time of the night before, sessions of several apps counted once.
    private suspend fun sleepMinutes(from: Instant, to: Instant): Int? {
        // Sessions that started a bit earlier are clipped to the window.
        val records = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(from.minus(Duration.ofHours(12)), to))
        ).records
        val sessions = records.map { record ->
            HealthMath.SleepSession(
                record.startTime, record.endTime,
                record.stages.map { HealthMath.SleepSession.Stage(it.startTime, it.endTime, it.stage) }
            )
        }
        return HealthMath.asleepMinutes(HealthMath.asleepIntervals(sessions), from, to)
    }

    private suspend fun latestRestingHeartRate(from: Instant, to: Instant): Int? =
        client.readRecords(
            ReadRecordsRequest(
                RestingHeartRateRecord::class, TimeRangeFilter.between(from, to),
                ascendingOrder = false, pageSize = 1
            )
        ).records.firstOrNull()?.beatsPerMinute?.toInt()

    // Health Connect's sum for the period counts what several apps recorded once.
    private suspend fun <T : Any> aggregate(
        metric: androidx.health.connect.client.aggregate.AggregateMetric<T>,
        from: Instant,
        to: Instant
    ): T? = client.aggregate(AggregateRequest(setOf(metric), TimeRangeFilter.between(from, to)))[metric]

    private suspend fun <T> orNull(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not allowed (SecurityException) or Health Connect failing: no value.
            null
        }

    // MARK: - Heart rate during trainings

    // Lowest, highest and average heart rate between the times, null without measurements.
    suspend fun heartRate(from: Instant, to: Instant): Training.HeartRate? {
        val result = client.aggregate(
            AggregateRequest(
                setOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX, HeartRateRecord.BPM_AVG),
                TimeRangeFilter.between(from, to)
            )
        )
        return HealthMath.heartRate(result[HeartRateRecord.BPM_MIN], result[HeartRateRecord.BPM_MAX], result[HeartRateRecord.BPM_AVG])
    }

    // Reads the training's heart rate and sends it; the training with it, null without measurements.
    suspend fun send(training: Training): Training? {
        if (!HealthMath.needsHeartRate(training)) return null
        val start = training.startedAt ?: return null
        val end = training.endedAt ?: return null
        val measured = heartRate(start, end) ?: return null
        return training.sendingHeartRate(measured)
    }

    // Sends the heart rate of the client's recent trainings that miss it,
    // once the client allowed reading it. Returns the trainings, updated.
    suspend fun sendMissing(trainings: List<Training>): List<Training> {
        if (!isAvailable || !isConnected()) return trainings
        val updated = trainings.associateBy { it.id }.toMutableMap()
        for (training in HealthMath.trainingsToSync(trainings, Instant.now())) {
            orNull { send(training) }?.let { updated[training.id] = it }
        }
        return trainings.map { updated[it.id] ?: it }
    }
}
