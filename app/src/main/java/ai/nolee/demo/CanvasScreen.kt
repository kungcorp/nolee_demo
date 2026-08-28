package ai.nolee.demo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val WarmBackground = Color(0xFFEADBD4)
private val Ink = Color(0xFF433139)
private val Cream = Color(0xFFFFF6EE)
private val Pink = Color(0xFFF39AB5)
private val Amber = Color(0xFFF3BD6C)
private val Sage = Color(0xFF8BB69A)
private val Blue = Color(0xFF768FA2)
private val Mauve = Color(0xFFAA8BD3)

private data class SceneCopy(val kicker: String, val first: String, val second: String, val body: String)

private val scenes = listOf(
    SceneCopy(
        "CAMERA LID · SCROLL",
        "MOVE",
        "MATTER",
        "A living form can become your personal AI's visual persona—listening, speaking and changing character as you interact.",
    ),
    SceneCopy(
        "SIDE BUTTON · F9",
        "PRESS",
        "SHIFT",
        "One control, timed your way. Short, double, triple and long become product gestures.",
    ),
    SceneCopy(
        "SENSORS · LIVE",
        "SENSE",
        "",
        "Live signals become motion, color and rhythm—not a forgotten diagnostics page.",
    ),
    SceneCopy(
        "YOUR APP · YOUR RULES",
        "MAKE",
        "YOURS",
        "Replace the experience from interface to boot art, then run it as one focused product.",
    ),
)

@Composable
fun CanvasScreen(
    scene: Int,
    sceneDirection: Int,
    compositionShift: Float,
    rotationSector: Int,
    pulseGeneration: Int,
    sensors: SensorSnapshot,
    thermal: LauncherThermal.Reading?,
    budget: LauncherThermal.Budget,
    forceSensors: Boolean,
    ownerControls: Boolean,
    status: String,
    onStep: (Int) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCloseOwnerControls: () -> Unit,
    onExitKiosk: () -> Unit,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .background(WarmBackground)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> drag += amount },
                    onDragEnd = {
                        if (drag < -36f) onStep(1) else if (drag > 36f) onStep(-1)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
            },
    ) {
        OrganicMatter(
            scene = scene,
            compositionShift = compositionShift,
            rotationSector = rotationSector,
            pulseGeneration = pulseGeneration,
            heartRate = sensors.heartRate,
            rollX = sensors.rollX,
            rollY = sensors.rollY,
            budget = budget,
        )
        Header(scene, sensors.heartRate)
        SceneWords(scene, sceneDirection)
        SensorPanel(
            visible = scene == 2 || forceSensors,
            snapshot = sensors,
            thermal = thermal,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = SensorCardBottomInset),
        )
        Progress(scene, Modifier.align(Alignment.BottomCenter).padding(horizontal = 30.dp, vertical = 29.dp))
        if (status.isNotBlank()) {
            Text(
                status,
                color = Ink.copy(alpha = .68f),
                fontSize = 8.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 38.dp),
            )
        }
        AnimatedVisibility(ownerControls, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
            OwnerControls(onCloseOwnerControls, onExitKiosk)
        }
    }
}

@Composable
private fun Header(scene: Int, heartRate: Int?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 53.dp, end = 53.dp, top = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "NOLEE / 0${scene + 1}",
            color = Ink,
            fontSize = 7.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
        )
        Row(
            Modifier.background(Cream.copy(alpha = .64f), CircleShape).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeartWave()
            Spacer(Modifier.width(4.dp))
            Text(
                heartRate?.toString() ?: "--",
                color = Ink,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(2.dp))
            Text("BPM", color = Ink.copy(alpha = .65f), fontSize = 6.sp)
        }
    }
}

@Composable
private fun HeartWave() {
    Canvas(Modifier.size(width = 28.dp, height = 13.dp)) {
        val p = Path().apply {
            moveTo(0f, size.height * .58f)
            lineTo(size.width * .18f, size.height * .58f)
            lineTo(size.width * .29f, size.height * .32f)
            lineTo(size.width * .4f, size.height * .82f)
            lineTo(size.width * .55f, size.height * .08f)
            lineTo(size.width * .7f, size.height * .58f)
            lineTo(size.width, size.height * .58f)
        }
        drawPath(p, Color(0xFFC95570), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun SceneWords(scene: Int, direction: Int) {
    AnimatedContent(
        targetState = scene,
        transitionSpec = {
            val sign = if (direction >= 0) 1 else -1
            (slideInHorizontally(tween(560, easing = FastOutSlowInEasing)) { sign * it } + fadeIn(tween(260)))
                .togetherWith(slideOutHorizontally(tween(420)) { -sign * it } + fadeOut(tween(220)))
        },
        label = "scene-copy",
    ) { index ->
        val copy = scenes[index]
        Box(Modifier.fillMaxSize()) {
            Text(
                copy.kicker,
                color = Ink,
                fontSize = 7.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.15.sp,
                modifier = Modifier.padding(start = 36.dp, top = 67.dp),
            )
            Text(
                copy.first,
                color = Ink,
                fontFamily = FontFamily.SansSerif,
                fontSize = 55.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-4).sp,
                modifier = Modifier.padding(start = 22.dp, top = 83.dp),
            )
            if (copy.second.isNotEmpty()) Text(
                copy.second,
                color = Cream.copy(alpha = .82f),
                fontFamily = FontFamily.SansSerif,
                fontSize = 55.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-4).sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 133.dp),
            )
            if (index != 2) {
                Text(
                    copy.body,
                    color = Ink.copy(alpha = .9f),
                    fontSize = 8.5.sp,
                    lineHeight = 11.5.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 34.dp, end = 102.dp, bottom = 82.dp),
                )
            }
            if (index != 2) {
                Text(
                    "0${index + 1} / 04",
                    color = Ink,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = .7.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 38.dp, bottom = 74.dp),
                )
            }
        }
    }
}

/** Distance from the bottom of the lens to the sensor card; also drives its safe-zone inset. */
private val SensorCardBottomInset = 76.dp

@Composable
private fun SensorPanel(
    visible: Boolean,
    snapshot: SensorSnapshot,
    thermal: LauncherThermal.Reading?,
    modifier: Modifier = Modifier,
) {
    // The card's nearest edge is the bottom curve, so that depth governs how far in its side sits.
    val endInset = safeContentInsetAt(SensorCardBottomInset, minimum = SensorCardEndInset)
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(tween(320)), exit = fadeOut(tween(180))) {
        Column(
            Modifier
                .padding(end = endInset)
                .width(SensorCardWidth),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SENSORS",
                    color = Ink.copy(alpha = .85f),
                    fontSize = instrumentSp(12f),
                    lineHeight = instrumentSp(15f),
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(6.dp).background(Color(0xFFE26D87), CircleShape))
            }
            Spacer(Modifier.height(9.dp))
            // One column, stacked: the card should read as a portrait instrument strip.
            Metric(
                "HEART RHYTHM",
                snapshot.heartRate?.toString() ?: "acquiring",
                "bpm".takeIf { snapshot.heartRate != null },
            )
            Spacer(Modifier.height(SensorMetricGap))
            Metric(
                "OXYGEN EST.",
                snapshot.oxygen?.toString() ?: "acquiring",
                "%".takeIf { snapshot.oxygen != null },
            )
            Spacer(Modifier.height(SensorMetricGap))
            Metric(
                "BP ESTIMATE",
                if (snapshot.systolic != null && snapshot.diastolic != null) {
                    "${snapshot.systolic}/${snapshot.diastolic}"
                } else {
                    "acquiring"
                },
                "mmHg".takeIf { snapshot.systolic != null && snapshot.diastolic != null },
            )
            Spacer(Modifier.height(SensorMetricGap))
            Metric("STEPS", snapshot.steps?.toString() ?: "waiting", null)
            Spacer(Modifier.height(SensorMetricGap))
            // Not a sensor the app owns — this one comes from the Launcher, and it is the number
            // the animation throttles itself against.
            Metric(
                "CPU TEMP",
                thermal?.cpu?.let { "%.0f".format(it) } ?: "no reading",
                "°C".takeIf { thermal?.cpu != null },
            )
        }
    }
}

/** Tuned with the placement tool. Paste its output over this block. */
private val SensorCardWidth = 96.dp
private val SensorMetricGap = 7.dp

/** Floor only: [safeContentInsetAt] raises it if the lens curve needs more. */
private val SensorCardEndInset = 10.dp

@Composable
private fun Metric(label: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            color = Ink.copy(alpha = .72f),
            fontSize = instrumentSp(11f),
            lineHeight = instrumentSp(13.5f),
            letterSpacing = .45.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        // The unit rides the reading's baseline at a smaller size: "138/82 mmHg" at full size does
        // not fit a column, and the number is what should be read first anyway.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = Ink,
                fontSize = instrumentSp(15f),
                lineHeight = instrumentSp(18f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            if (unit != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    unit,
                    color = Ink.copy(alpha = .72f),
                    fontSize = instrumentSp(8f),
                    lineHeight = instrumentSp(10f),
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

@Composable
private fun Progress(scene: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(if (index == scene) Ink.copy(alpha = .86f) else Ink.copy(alpha = .18f)),
            )
        }
    }
}

@Composable
private fun OwnerControls(onClose: () -> Unit, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xE8231D22))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Amber,
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(89.6.dp.toPx()),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))),
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 45.dp),
        ) {
            Text("PHYSICAL DISPLAY SAFE ZONE", color = Amber, fontSize = 7.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "OWNER\nCONTROLS",
                color = Cream,
                fontSize = 31.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-2).sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Long side-button always reveals the tested way out. Essential controls remain inside the lens boundary.",
                color = Cream.copy(alpha = .82f),
                fontSize = 8.sp,
                lineHeight = 11.sp,
            )
            Spacer(Modifier.height(13.dp))
            OwnerButton("EXIT KIOSK", Amber, Ink, onExit)
            Spacer(Modifier.height(7.dp))
            OwnerButton("RETURN TO SCENE", Cream.copy(alpha = .09f), Cream, onClose)
        }
    }
}

@Composable
private fun OwnerButton(label: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(background, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = .7.sp)
    }
}

private data class Strand(
    val x: Float,
    val y: Float,
    val z: Float,
    val length: Float,
    val width: Float,
    val bend: Float,
    val phase: Float,
    val color: Color,
)

/**
 * The gas cloud is rasterised once and blitted every frame.
 *
 * Drawing it live cost 21 radial-gradient shaders per frame, which is what was heating the watch.
 * Baked, the density is free: the texture can hold far more puffs than the live version ever could.
 */
private const val CoreTexturePx = 384

private const val TwoPi = 6.2831855f

/** Texture edge, in orb radii. The atmosphere reaches 1.9, so the square side is twice that. */
private const val CoreTextureExtent = 3.8f

private data class Puff(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val drift: Float,
    val phase: Float,
    val color: Color,
)

private fun buildCoreTexture(): ImageBitmap {
    val bitmap = ImageBitmap(CoreTexturePx, CoreTexturePx, ImageBitmapConfig.Argb8888)
    val extent = CoreTexturePx.toFloat()
    val middle = Offset(extent * .5f, extent * .5f)
    // Orb radii -> texture pixels.
    val unit = extent * .5f / (CoreTextureExtent * .5f)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GraphicsCanvas(bitmap), Size(extent, extent)) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to Cream.copy(alpha = .11f),
                .55f to Pink.copy(alpha = .07f),
                .84f to Cream.copy(alpha = .14f),
                1f to Color.Transparent,
                center = middle,
                radius = unit * 1.9f,
            ),
            radius = unit * 1.9f,
            center = middle,
        )
        val random = Random(41)
        val tints = listOf(
            Pink,
            Color(0xFFFFFCF7),
            Sage,
            Amber,
            Color(0xFFFDF6EF),
            Mauve,
            Color(0xFFE7AD8B),
            Blue,
        )
        repeat(58) { index ->
            val angle = random.nextFloat() * Math.PI.toFloat() * 2f
            val spread = sqrt(random.nextFloat()) * .64f
            val puffCenter = middle + Offset(cos(angle) * spread * unit, sin(angle) * spread * .88f * unit)
            val radius = (.3f + random.nextFloat() * .6f) * unit
            val alpha = .13f + random.nextFloat() * .16f
            val tint = tints[index % tints.size]
            drawCircle(
                brush = Brush.radialGradient(
                    0f to tint.copy(alpha = alpha),
                    .5f to tint.copy(alpha = alpha * .62f),
                    1f to Color.Transparent,
                    center = puffCenter,
                    radius = radius,
                ),
                radius = radius,
                center = puffCenter,
            )
        }
    }
    return bitmap
}

@Composable
private fun OrganicMatter(
    scene: Int,
    compositionShift: Float,
    rotationSector: Int,
    pulseGeneration: Int,
    heartRate: Int?,
    rollX: Float,
    rollY: Float,
    budget: LauncherThermal.Budget,
) {
    val strands = remember {
        val random = Random(73)
        val colors = listOf(Pink, Amber, Sage, Blue, Color(0xFFD99CA1), Mauve, Color(0xFFE7AD8B))
        List(104) { index ->
            val angle = random.nextFloat() * Math.PI.toFloat() * 2f
            val z = random.nextFloat() * 2f - 1f
            val radius = sqrt(1f - z * z)
            Strand(
                x = radius * cos(angle),
                y = radius * sin(angle),
                z = z,
                length = 28f + random.nextFloat() * 53f,
                width = 1f + random.nextFloat() * 2.5f,
                bend = (random.nextFloat() - .5f) * 24f,
                phase = random.nextFloat() * 6.28f,
                color = colors[index % colors.size],
            )
        }
    }
    val tufts = remember {
        val random = Random(211)
        val colors = listOf(Pink, Amber, Sage, Blue, Color(0xFFD99CA1), Mauve, Color(0xFFE7AD8B))
        List(52) { index ->
            val angle = random.nextFloat() * Math.PI.toFloat() * 2f
            val z = random.nextFloat() * 2f - 1f
            val radius = sqrt(1f - z * z) * (.24f + random.nextFloat() * .42f)
            Strand(
                x = radius * cos(angle),
                y = radius * sin(angle),
                z = z,
                length = 9f + random.nextFloat() * 19f,
                width = .8f + random.nextFloat() * 1.2f,
                bend = (random.nextFloat() - .5f) * 13f,
                phase = random.nextFloat() * 6.28f,
                color = colors[index % colors.size],
            )
        }
    }
    val coreTexture = remember { buildCoreTexture() }
    // One throttled clock instead of two per-frame animations. The form drifts slowly, so halving
    // the redraw rate is invisible in motion and halves the GPU work that was cooking the watch.
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> clock.floatValue = (now - start) / 1_000_000_000f }
            kotlinx.coroutines.delay(budget.frameGapMs)
        }
    }
    val beatsPerMinute = (heartRate ?: 72).coerceIn(40, 180)
    var tap by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pulseGeneration) {
        if (pulseGeneration == 0) return@LaunchedEffect
        tap = 1f
        kotlinx.coroutines.delay(180)
        tap = 0f
    }
    val tapScale by animateFloatAsState(1f + tap * .16f, tween(240), label = "tap")
    val targetShift = when (scene) { 1 -> 70f; 2 -> -82f; 3 -> 18f; else -> 0f } + compositionShift
    val shift by animateFloatAsState(targetShift, tween(520, easing = FastOutSlowInEasing), label = "shift")
    val sector by animateFloatAsState(rotationSector * .7f, tween(430), label = "sector")
    // A ball on glass follows gravity and overshoots slightly before it settles.
    val rollSpring = spring<Float>(dampingRatio = .58f, stiffness = Spring.StiffnessVeryLow)
    val rolledX by animateFloatAsState(rollX, rollSpring, label = "roll-x")
    val rolledY by animateFloatAsState(rollY, rollSpring, label = "roll-y")
    val strandPath = remember { Path() }

    Canvas(Modifier.fillMaxSize()) {
        val time = clock.floatValue
        val spin = time / 18f * TwoPi
        // Two beats per breath, as before, just expressed against the shared clock.
        val breathe = .96f + .085f * (1f + sin(time / (120f / beatsPerMinute) * TwoPi)) * .5f
        val scale = size.minDimension * .23f * breathe * tapScale
        // Keep the form on the glass: it can roll until it is nearly touching an edge, no further.
        val roomX = (size.width * .5f - scale * 1.05f).coerceAtLeast(0f)
        val roomY = (size.height * .5f - scale * 1.05f).coerceAtLeast(0f)
        val center = Offset(
            size.width * .5f + shift.dp.toPx() + rolledX * roomX,
            size.height * .47f + rolledY * roomY,
        )
        val rotation = spin + sector
        fun order(source: List<Strand>) = source.map { strand ->
            val c = cos(rotation)
            val s = sin(rotation)
            Triple(strand, strand.x * c - strand.z * s, strand.x * s + strand.z * c)
        }.sortedBy { it.third }
        val ordered = strands.map { strand ->
            val c = cos(rotation)
            val s = sin(rotation)
            val rx = strand.x * c - strand.z * s
            val rz = strand.x * s + strand.z * c
            Triple(strand, rx, rz)
        }.sortedBy { it.third }
        val orderedTufts = order(tufts)

        fun drawStrand(item: Triple<Strand, Float, Float>) {
            // Reused across every filament: allocating a Path per strand per frame is pure GC churn.
            val strand = item.first
            val rx = item.second
            val rz = item.third
            val depth = (rz + 1f) * .5f
            val start = Offset(
                center.x + rx * scale,
                center.y + strand.y * scale,
            )
            val reach = strand.length.dp.toPx() * (.65f + depth * .65f)
            val end = Offset(start.x + rx * reach, start.y + strand.y * reach)
            val control = Offset(
                (start.x + end.x) * .5f + strand.bend.dp.toPx(),
                (start.y + end.y) * .5f - strand.bend.dp.toPx() * .25f,
            )
            strandPath.reset()
            strandPath.moveTo(start.x, start.y)
            strandPath.quadraticTo(control.x, control.y, end.x, end.y)
            drawPath(
                strandPath,
                strand.color.copy(alpha = .36f + depth * .6f),
                style = Stroke(strand.width.dp.toPx() * (.55f + depth), cap = StrokeCap.Round),
            )
            drawCircle(
                strand.color.copy(alpha = .5f + depth * .5f),
                radius = (1.6f + strand.width * (.65f + depth)).dp.toPx(),
                center = end,
            )
        }

        // Back filaments disappear behind the body. Front filaments are drawn later over it,
        // which gives the form real visual depth instead of reading as a flat spiky badge.
        val visible = budget.filaments
        ordered.filter { it.third < 0f }.take(visible / 2).forEach(::drawStrand)

        // One blit for atmosphere and gas together. A slow counter-rotation keeps it alive.
        val texExtent = scale * CoreTextureExtent
        val texOffset = IntOffset(
            (center.x - texExtent * .5f).roundToInt(),
            (center.y - texExtent * .5f).roundToInt(),
        )
        val texSize = IntSize(texExtent.roundToInt(), texExtent.roundToInt())
        rotate(-spin * 14f, center) {
            drawImage(coreTexture, dstOffset = texOffset, dstSize = texSize, alpha = .92f)
        }

        orderedTufts.forEach(::drawStrand)

        ordered.filter { it.third >= 0f }.take(visible / 2).forEach(::drawStrand)

        // The same texture again, barely there, so filaments sit inside the gas and not on top of it.
        rotate(spin * 9f, center) {
            drawImage(coreTexture, dstOffset = texOffset, dstSize = texSize, alpha = .16f)
        }

        // A few soft particles crossing the face make the front/back split visible in motion.
        ordered.asReversed().take(18).forEach { (strand, rx, rz) ->
            val particle = center + Offset(rx * scale * .72f, strand.y * scale * .72f)
            drawCircle(
                color = strand.color.copy(alpha = .2f + (rz + 1f) * .12f),
                radius = (1.8f + strand.width * .55f).dp.toPx(),
                center = particle,
            )
        }
    }
}
