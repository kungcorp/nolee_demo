package ai.nolee.demo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Heart rate, blood oxygen and blood pressure, measured by this app.
 *
 * ⚠️ **These sensors return a stale constant unless the device is put into measurement mode first.**
 * Registering a listener on its own gets the last value republished at roughly 3 Hz forever, which
 * looks exactly like a working reading and is not one. Measurement mode is three things together,
 * and none of them work alone:
 *
 *   1. `health_measure_type` set to the metric you want
 *   2. `health_measure_status` set to 1
 *   3. a `PARTIAL_WAKE_LOCK` held, and a listener registered on that metric's own sensor
 *
 * ⚠️ **The three cannot be measured at once.** The measurement type decides what the hardware
 * produces and every sensor mirrors it, so declaring one type and registering all three returns the
 * same figure echoed three ways. Each is asked for on its own — see
 * [VitalsSequencer.measureIfStale].
 *
 * ⚠️ **Writing those settings needs `WRITE_SECURE_SETTINGS`.** It is not a root permission: it
 * carries Android's `development` flag, so any app that declares it can be granted it over adb.
 *
 *     adb shell pm grant ai.nolee.demo android.permission.WRITE_SECURE_SETTINGS
 *
 * Without the grant every metric reports [Reading.Failed] rather than failing silently.
 */
enum class Vital(
    val label: String,
    val unit: String,
    /** Value for `health_measure_type`; also decides which indices the sensor populates. */
    val measureType: Int,
    /** Passed to `getDefaultSensor`. A vendor type id — `TYPE_HEART_RATE` returns nothing here. */
    val sensorType: Int,
    val valueIndex: Int,
    val secondIndex: Int? = null,
) {
    // Declaration order is measurement order.
    HEART("HEART RATE", "bpm", 1, 65599, 1),
    OXYGEN("BLOOD OXYGEN", "%", 2, 65596, 2),
    PRESSURE("BLOOD PRESSURE", "mmHg", 3, 65598, 3, 4),
}

sealed interface Reading {
    /** Not measured yet. */
    object Pending : Reading

    /** Being measured right now — this is what the card animates. */
    object Acquiring : Reading

    data class Value(val value: Int, val second: Int?, val atMillis: Long) : Reading {
        fun ageSeconds(now: Long = System.currentTimeMillis()): Long =
            ((now - atMillis) / 1000).coerceAtLeast(0)
    }

    data class Failed(val reason: String) : Reading
}

data class VitalsState(
    val readings: Map<Vital, Reading> = Vital.values().associateWith { Reading.Pending },
    /** The metric being measured, or null when no cycle is running. */
    val active: Vital? = null,
) {
    operator fun get(vital: Vital): Reading = readings[vital] ?: Reading.Pending
}

/**
 * Measures one vital at a time, on request.
 *
 * ⚠️ **A measurement drives the PPG LEDs for its whole 30 s** — the green light on the back — so it
 * costs battery and warms the device. That is why nothing here runs on a timer: [measureIfStale]
 * measures only the metric being looked at, only when its reading has gone stale, and cancelling
 * the calling coroutine stops the hardware immediately.
 */
class VitalsSequencer(
    context: Context,
    private val onState: (VitalsState) -> Unit,
) {

    private val app = context.applicationContext
    private val sensors = app.getSystemService(SensorManager::class.java)
    private val power = app.getSystemService(PowerManager::class.java)

    /** Sensor callbacks are delivered here, so registration works from any thread. */
    private val handler = Handler(Looper.getMainLooper())

    private var state = VitalsState()
        set(value) {
            field = value
            onState(value)
        }

    /**
     * Measure one [vital], if its reading is older than [maxAgeMillis] or there is none.
     *
     * ⚠️ **One metric, not all three.** Each is its own 30 s window and they cannot be combined, so
     * measuring the set costs 90 s — far too long to sit through when the reading you came for is
     * the blood pressure. The UI shows one vital per page and asks for that one alone; the other
     * two stay as they were until you swipe to them.
     *
     * Cancel the calling coroutine to abort: the window releases the sensor and leaves measurement
     * mode in its `finally`, so swiping away mid-measurement turns the LEDs back off.
     */
    suspend fun measureIfStale(vital: Vital, maxAgeMillis: Long = STALE_AFTER_MS) {
        val existing = state[vital] as? Reading.Value
        if (existing != null && System.currentTimeMillis() - existing.atMillis < maxAgeMillis) return
        state = state.copy(active = vital, readings = state.readings + (vital to Reading.Acquiring))
        val reading = measure(vital)
        state = state.copy(active = null, readings = state.readings + (vital to reading))
    }

    /**
     * One metric, one 30 s window.
     *
     * The window length is the vendor's own `DELAY_TIME`, and the value kept is the last one the
     * sensor produced inside it rather than the first — an early sample is mid-acquisition.
     */
    private suspend fun measure(vital: Vital): Reading {
        val manager = sensors ?: return Reading.Failed("no sensors")
        val sensor = manager.getDefaultSensor(vital.sensorType)
            ?: return Reading.Failed("unavailable")
        if (!setMeasurementMode(vital.measureType, on = true)) {
            return Reading.Failed("needs permission")
        }

        val lock = runCatching {
            power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
                ?.apply { acquire(WINDOW_MS + 5_000L) }
        }.getOrNull()

        var value: Reading.Value? = null
        var contact = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // values[0] is skin contact on every metric. Zero is the vendor's error case, not
                // a measurement of zero.
                if ((event.values.getOrNull(0)?.toInt() ?: 0) == 0) return
                contact = true
                val first = event.values.getOrNull(vital.valueIndex)?.toInt() ?: return
                if (first <= 0) return
                val second = vital.secondIndex?.let { event.values.getOrNull(it)?.toInt() }
                // Blood pressure is only a reading when both halves are present.
                if (vital.secondIndex != null && (second == null || second <= 0)) return
                value = Reading.Value(first, second, System.currentTimeMillis())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        try {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
            delay(WINDOW_MS)
        } finally {
            manager.unregisterListener(listener)
            setMeasurementMode(0, on = false)
            runCatching { lock?.let { if (it.isHeld) it.release() } }
        }

        return value ?: Reading.Failed(if (contact) "no value" else "not worn")
    }

    /** False when `WRITE_SECURE_SETTINGS` has not been granted — see the class docs. */
    private fun setMeasurementMode(type: Int, on: Boolean): Boolean = runCatching {
        val resolver = app.contentResolver
        if (on && !Settings.Global.putInt(resolver, KEY_TYPE, type)) return@runCatching false
        Settings.Global.putInt(resolver, KEY_STATUS, if (on) 1 else 0)
    }.getOrElse {
        Log.w(TAG, "cannot enter measurement mode — is WRITE_SECURE_SETTINGS granted?", it)
        false
    }

    private companion object {
        const val TAG = "NoleeDemo/Vitals"
        const val KEY_TYPE = "health_measure_type"
        const val KEY_STATUS = "health_measure_status"
        const val WAKE_TAG = "nolee-demo:vitals"

        /** The vendor's own measurement window. Shorter risks reporting a half-acquired value. */
        const val WINDOW_MS = 30_000L

        /** How old a set of readings may be before arriving at the card measures again. */
        const val STALE_AFTER_MS = 3 * 60 * 1000L
    }
}
