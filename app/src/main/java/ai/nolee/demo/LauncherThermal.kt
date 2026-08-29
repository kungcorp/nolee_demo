package ai.nolee.demo

import android.content.Context
import android.net.Uri

/**
 * Device temperature from Nolee Launcher — no root, no permission, no client library.
 *
 * The kernel's thermal zones are unreadable from an app uid and `dumpsys battery` is a stub on this
 * ROM, so the Launcher reads them and republishes two roles:
 *
 * - **cpu** rises with your own workload. Throttle against this one.
 * - **skin** tracks the case and ambient. Warn a wearer against this one.
 *
 * ⚠️ **The platform gives you numbers, not a verdict.** Nothing is calibrated against an external
 * reference, so there is no official "too hot" — [Budget] below is *this app's* opinion, measured
 * on this app's workload, and yours should be your own. Prefer reacting to a rise over an absolute.
 */
object LauncherThermal {

    private val STATE_URI: Uri = Uri.parse("content://io.kungcorp.nolee.launcher.state")

    data class Reading(val soc: Float?, val board: Float?, val source: String)

    /** **Blocking** — the Launcher shells out to read the zones. Never call on the main thread. */
    fun read(context: Context): Reading? = runCatching {
        val out = context.contentResolver.call(STATE_URI, "thermal", null, null) ?: return null
        if (!out.getBoolean("ok", false)) return null
        Reading(
            soc = if (out.containsKey("soc_c")) out.getFloat("soc_c") else null,
            board = if (out.containsKey("board_c")) out.getFloat("board_c") else null,
            source = out.getString("thermal_source") ?: "unknown",
        )
    }.getOrNull()

    /**
     * How hard this particular app is willing to work at a given temperature.
     *
     * Calibrated 2026-08-29 against a worn probe run (E122), not guessed. Across 275 samples from a
     * fanned-cold start to full load, `skin` tracked `cpu` as **skin ≈ 0.68 × cpu + 24.3** (R² 0.98),
     * and the wearer's own boundaries were: comfortable up to skin 60, warm 60–70, hot beyond 70,
     * with "keep a worn app under skin 68" as their design guidance.
     *
     * So [WARM] is cpu 52 (≈ skin 60, where comfortable ends) and [HOT] is cpu 64 (≈ skin 68, the
     * worn ceiling). Copy the method, not the constants: these are one wearer, one room, one
     * device, and your app's own load profile will differ.
     *
     * ⚠️ **Decide whether your app is worn or docked before choosing limits.** This demo throttles
     * on [Reading.cpu], because it is a showcase that runs on a bench as often as on a wrist and
     * the concern is throughput, not comfort. An app worn against skin should gate on
     * [Reading.skin] instead and be stricter, since nobody notices a warm die but everybody
     * notices a warm case. An app that runs both ways wants two sets of limits, not one.
     */
    enum class Budget(val frameGapMs: Long, val filaments: Int) {
        /** Full detail, ~30 fps. */
        FULL(20L, 104),

        /** ~20 fps and a thinner form. Barely visible in motion; measurably less work. */
        WARM(40L, 72),

        /** ~12 fps. The scene stays alive but stops driving the temperature up. */
        HOT(70L, 48),
        ;

        companion object {
            /** Absent readings return [FULL]: never let a failed read look like a cool device. */
            fun forSoc(celsius: Float?): Budget = when {
                celsius == null -> FULL
                celsius >= 64f -> HOT
                celsius >= 52f -> WARM
                else -> FULL
            }
        }
    }

    /**
     * Seconds between polls. Each read costs a privileged read inside the Launcher, so poll lazily
     * while the number is only driving the throttle, and more often while it is on screen where a
     * stale figure would be visible.
     */
    const val POLL_SECONDS_HIDDEN = 60L
    const val POLL_SECONDS_VISIBLE = 15L
}
