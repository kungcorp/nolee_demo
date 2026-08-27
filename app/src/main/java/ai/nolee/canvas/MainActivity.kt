package ai.nolee.canvas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var lidScroll: LidScrollBridge
    private lateinit var sensors: NoleeSensors

    private var scene by mutableIntStateOf(0)
    private var sceneDirection by mutableIntStateOf(1)
    private var compositionShift by mutableFloatStateOf(0f)
    private var rotationSector by mutableIntStateOf(0)
    private var pulseGeneration by mutableIntStateOf(0)
    private var snapshot by mutableStateOf(SensorSnapshot())
    private var forceSensors by mutableStateOf(false)
    private var ownerControls by mutableStateOf(false)
    private var status by mutableStateOf("")
    private var bodyPermission by mutableStateOf(false)
    private var budget by mutableStateOf(LauncherThermal.Budget.FULL)
    private var thermal by mutableStateOf<LauncherThermal.Reading?>(null)

    private var sideDownAt = 0L
    private var sideHeld = false
    private var sidePressCount = 0
    private var lastStepCount: Int? = null
    private var stepsSinceSceneChange = 0

    private val longSide = Runnable {
        if (!sideHeld) return@Runnable
        ownerControls = true
        status = "SIDE · LONG / OWNER CONTROLS"
        haptic(70)
    }

    private val commitSidePresses = Runnable {
        val count = sidePressCount
        sidePressCount = 0
        when (count) {
            1 -> {
                compositionShift = if (compositionShift >= 0f) -42f else 42f
                status = "SIDE · SHORT / SHIFT FORM"
            }
            2 -> {
                changeScene(1)
                status = "SIDE · DOUBLE / NEXT SCENE"
            }
            else -> {
                showSensorScene()
                status = "SIDE · TRIPLE / LIVE SENSORS"
            }
        }
        haptic(42)
    }

    private val bodySensorPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        bodyPermission = granted
        if (!granted) status = "BODY SENSORS NEED PERMISSION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        sensors = NoleeSensors(this) { next ->
            runOnUiThread {
                reactToSteps(next.steps)
                snapshot = next
            }
        }
        lidScroll = LidScrollBridge(this) { direction ->
            runOnUiThread {
                changeScene(direction)
                status = if (direction > 0) "LID · STEP UP" else "LID · STEP DOWN"
                haptic(34)
            }
        }

        setContent {
            MaterialTheme {
                BackHandler(enabled = ownerControls) { ownerControls = false }
                // The optical sensors run only while their readings are on screen. Values already
                // taken stay in the snapshot, so the header keeps the last heart rate it measured.
                LaunchedEffect(scene, forceSensors, bodyPermission) {
                    sensors.setBodySensors(bodyPermission && (scene == 2 || forceSensors))
                }
                // Watch our own heat and back off before the device gets uncomfortable. The
                // thresholds are this app's, measured against this animation — the platform
                // publishes numbers, not a verdict. See LauncherThermal.Budget.
                LaunchedEffect(Unit) {
                    while (true) {
                        val reading = withContext(Dispatchers.IO) { LauncherThermal.read(this@MainActivity) }
                        thermal = reading
                        val next = LauncherThermal.Budget.forCpu(reading?.cpu)
                        if (next != budget) {
                            budget = next
                            status = "THERMAL · ${next.name} / ${reading?.cpu?.roundToInt()}°C CPU"
                        }
                        delay(LauncherThermal.POLL_SECONDS * 1000)
                    }
                }
                CanvasScreen(
                    scene = scene,
                    sceneDirection = sceneDirection,
                    compositionShift = compositionShift,
                    rotationSector = rotationSector,
                    pulseGeneration = pulseGeneration,
                    sensors = snapshot,
                    thermal = thermal,
                    budget = budget,
                    forceSensors = forceSensors,
                    ownerControls = ownerControls,
                    status = status,
                    onStep = { direction ->
                        changeScene(direction)
                        status = if (direction > 0) "TOUCH · NEXT SCENE" else "TOUCH · PREVIOUS SCENE"
                    },
                    onTap = ::pulseMatter,
                    onLongPress = {
                        ownerControls = true
                        status = "TOUCH · OWNER CONTROLS"
                    },
                    onCloseOwnerControls = { ownerControls = false },
                    onExitKiosk = ::exitKiosk,
                )
            }
        }

        if (!hasBodySensorPermission()) bodySensorPermission.launch(Manifest.permission.BODY_SENSORS)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        bodyPermission = hasBodySensorPermission()
        sensors.start()
        val error = lidScroll.start()
        if (error != null) status = "LID BRIDGE · $error"
    }

    override fun onPause() {
        lidScroll.stop()
        sensors.stop()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_F9) {
            if (event?.repeatCount == 0) {
                sideDownAt = SystemClock.uptimeMillis()
                sideHeld = true
                mainHandler.removeCallbacks(longSide)
                mainHandler.postDelayed(longSide, LONG_PRESS_MS)
            }
            return true
        }
        if (event?.repeatCount != 0) return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_F2 -> {
                pulseMatter()
                status = "LID · TAP / PULSE PERSONA"
                haptic(34)
                true
            }
            KeyEvent.KEYCODE_F5 -> {
                showSensorScene()
                status = "LID · DOUBLE TOUCH / SENSOR STREAM"
                haptic(46)
                true
            }
            KeyEvent.KEYCODE_F6 -> {
                sceneDirection = -1
                scene = 0
                pulseMatter()
                status = "LID · LONG TOUCH / PERSONA LISTENING"
                haptic(65)
                true
            }
            KeyEvent.KEYCODE_F3,
            KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_F7,
            KeyEvent.KEYCODE_F8,
            -> handleLidPositionOrCrossSwipe(keyCode, event)
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode != KeyEvent.KEYCODE_F9) return super.onKeyUp(keyCode, event)
        sideHeld = false
        mainHandler.removeCallbacks(longSide)
        val heldFor = SystemClock.uptimeMillis() - sideDownAt
        if (ownerControls || heldFor >= LONG_PRESS_MS) {
            ownerControls = true
            status = "SIDE · LONG / OWNER CONTROLS"
            return true
        }

        sidePressCount++
        mainHandler.removeCallbacks(commitSidePresses)
        if (sidePressCount >= 3) mainHandler.post(commitSidePresses)
        else mainHandler.postDelayed(commitSidePresses, MULTI_PRESS_GAP_MS)
        return true
    }

    private fun handleLidPositionOrCrossSwipe(keyCode: Int, event: KeyEvent?): Boolean {
        val deviceName = event?.device?.name.orEmpty()
        val isCrossSwipe = deviceName == "madev" || event?.scanCode == 468 || event?.scanCode == 469
        if (isCrossSwipe && (keyCode == KeyEvent.KEYCODE_F3 || keyCode == KeyEvent.KEYCODE_F4)) {
            val shift = if (keyCode == KeyEvent.KEYCODE_F3) -56f else 56f
            compositionShift = shift
            pulseMatter()
            status = "LID · CROSS-SWIPE / BEND FORM"
            haptic(32)
            return true
        }

        if (deviceName == "och1970" || keyCode == KeyEvent.KEYCODE_F7 || keyCode == KeyEvent.KEYCODE_F8) {
            when (keyCode) {
                KeyEvent.KEYCODE_F3 -> {
                    rotationSector = -1
                    status = "CAMERA · FRONT SECTOR"
                }
                KeyEvent.KEYCODE_F4 -> {
                    rotationSector = 1
                    status = "CAMERA · BACK SECTOR"
                }
                KeyEvent.KEYCODE_F8 -> {
                    rotationSector = 0
                    status = "CAMERA · SIDE SECTOR"
                }
                KeyEvent.KEYCODE_F7 -> {
                    rotationSector = 0
                    compositionShift = 0f
                    status = "CAMERA · SEATED FLUSH"
                }
            }
            pulseMatter()
            return true
        }
        return false
    }

    private fun pulseMatter() {
        pulseGeneration++
    }

    private fun showSensorScene() {
        sceneDirection = 1
        scene = 2
        forceSensors = true
        pulseMatter()
    }

    private fun changeScene(direction: Int) {
        sceneDirection = if (direction >= 0) 1 else -1
        scene = (scene + sceneDirection + SCENE_COUNT) % SCENE_COUNT
        forceSensors = scene == 2
        pulseMatter()
    }

    private fun reactToSteps(total: Int?) {
        val previous = lastStepCount
        lastStepCount = total
        if (total == null || previous == null || total <= previous) return
        stepsSinceSceneChange += total - previous
        pulseMatter()
        if (stepsSinceSceneChange >= STEPS_PER_SCENE) {
            stepsSinceSceneChange %= STEPS_PER_SCENE
            changeScene(1)
            status = "MOTION · STEPS ADVANCED THE SCENE"
        }
    }

    private fun exitKiosk() {
        lifecycleScope.launch {
            val result = NoleeKiosk.exit(this@MainActivity)
            if (result.ok) return@launch
            Toast.makeText(
                this@MainActivity,
                result.reason ?: "Could not exit kiosk.",
                Toast.LENGTH_LONG,
            ).show()
            status = "EXIT KIOSK · ${result.reason ?: "UNAVAILABLE"}"
        }
    }

    private fun hasBodySensorPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun haptic(durationMs: Long) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, 105))
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val SCENE_COUNT = 4
        const val LONG_PRESS_MS = 600L
        const val MULTI_PRESS_GAP_MS = 310L
        const val STEPS_PER_SCENE = 10
    }
}
