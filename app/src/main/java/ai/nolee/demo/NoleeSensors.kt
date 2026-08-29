package ai.nolee.demo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SensorSnapshot(
    val steps: Int? = null,
    val tiltDegrees: Float = 0f,
    /** In-plane gravity, -1..1, as the screen sees it: +x is right, +y is down. */
    val rollX: Float = 0f,
    val rollY: Float = 0f,
)

class NoleeSensors(
    context: Context,
    private val onSnapshot: (SensorSnapshot) -> Unit,
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private var current = SensorSnapshot()
    private val all by lazy { manager.getSensorList(Sensor.TYPE_ALL) }

    /**
     * Motion and steps only — the optical sensors are not this class's business.
     *
     * ⚠️ Heart rate, SpO2 and blood pressure are **not** readable this way. Registering
     * `heart_rate` / `spo2` / `vp` and reading their values returns a stale constant republished at
     * roughly 3 Hz, which is indistinguishable from a live reading and is not one. They need
     * measurement mode, they can only be taken one at a time, and [VitalsSequencer] owns all of
     * that — including switching the PPG LEDs off again afterwards.
     */
    fun start() {
        manager.unregisterListener(this)
        sensor("android.sensor.accelerometer")?.let {
            // UI rate is ample: the roll is spring-smoothed, and GAME rate only added noise wakeups.
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensor("android.sensor.step_counter")?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val next = when (event.sensor.stringType) {
            "android.sensor.accelerometer" -> {
                val x = event.values.getOrElse(0) { 0f }
                val y = event.values.getOrElse(1) { 0f }
                val z = event.values.getOrElse(2) { 9.81f }
                val horizontal = sqrt(x * x + y * y)
                // An axis reads +g when it points up, so gravity runs the other way. Flipping x and
                // keeping y gives the direction a ball resting on the glass would actually roll.
                //
                // Quantised so a watch sitting still stops emitting: an unquantised value jitters
                // on every sample, and each one re-targeted the roll spring, which kept the whole
                // screen animating and defeated the frame throttle.
                current.copy(
                    tiltDegrees = quantise(
                        Math.toDegrees(atan2(horizontal, z).toDouble()).toFloat(),
                        TILT_STEP_DEGREES,
                    ),
                    rollX = quantise((-x / GRAVITY).coerceIn(-1f, 1f), ROLL_STEP),
                    rollY = quantise((y / GRAVITY).coerceIn(-1f, 1f), ROLL_STEP),
                )
            }
            "android.sensor.step_counter" -> current.copy(
                steps = event.values.getOrNull(0)?.takeIf { it >= 0f }?.toInt() ?: current.steps,
            )
            else -> current
        }
        if (next == current) return
        current = next
        onSnapshot(current)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun sensor(stringType: String): Sensor? = all.firstOrNull { it.stringType == stringType }

    private fun quantise(value: Float, step: Float): Float = (value / step).roundToInt() * step

    private companion object {
        const val GRAVITY = 9.81f
        const val ROLL_STEP = 0.02f
        const val TILT_STEP_DEGREES = 0.5f
    }
}
