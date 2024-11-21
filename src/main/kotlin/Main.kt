import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.onDrag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import com.darkrockstudios.libraries.mpfilepicker.MultipleFilePicker
import kotlinx.coroutines.*
import styles.ui.theme.AppTheme
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.random.Random
import kotlin.random.nextInt

const val STEP_MULTIPLIER = 30

const val SLIDER_WEIGHT = 5f
const val SLIDER_VALUE_WEIGHT = .7f
const val SLIDER_LABEL_WEIGHT = 1.3f

enum class Picking {
    None, FileIn, DirIn, DirOut
}

val random = Random(30)

data class Element(
    val x: MutableState<Float>,
    val y: MutableState<Float>,
    val anglePower: MutableState<Float>,
    val sizeDeviationPower: MutableState<Float>,
    val image: BufferedImage
)

var elements by mutableStateOf(emptyList<Element>())

fun String.toPositiveIntOrNull() = toIntOrNull()?.takeIf { it > 0 }

fun randomize(density: Float, w: Int, h: Int, images: List<BufferedImage>, random: Random) {
    val stepX = (1 / density).toInt() * STEP_MULTIPLIER
    val stepY = (1 / density).toInt() * STEP_MULTIPLIER
    elements = if (images.isEmpty()) emptyList()
    else (0..<w step stepX).flatMap { x ->
        (0..<h step stepY).map { y ->
            val nx = (x + random.nextInt((-stepX / 2)..(stepX / 2))) / w.toFloat()
            val ny = (y + random.nextInt((-stepY / 2)..(stepY / 2))) / h.toFloat()
            Element(
                mutableStateOf(nx),
                mutableStateOf(ny),
                anglePower = mutableStateOf(random.nextFloat()),
                sizeDeviationPower = mutableStateOf((random.nextFloat() * 2) - 1),
                image = images.random(random)
            )
        }
    }
}

fun generateImage(
    w: Int,
    h: Int,
    scaleX: Float,
    scaleY: Float,
    rotation: Float,
    sizeDeviation: Int,
): BufferedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
    elements.forEach { (x, y, anglePower, sizeDeviationPower, image) ->
        val dsX = scaleX + scaleX * sizeDeviationPower.value * (sizeDeviation / 100f)
        val dsY = scaleY + scaleY * sizeDeviationPower.value * (sizeDeviation / 100f)
        draw(
            image,
            (x.value * w).toInt(),
            (y.value * h).toInt(),
            dsX,
            dsY,
            rotation * anglePower.value,
        )
    }
}

fun BufferedImage.draw(
    src: BufferedImage,
    startX: Int,
    startY: Int,
    scaleX: Float,
    scaleY: Float,
    rotation: Float,
    anchorX: Double = 0.5,
    anchorY: Double = 0.5,
) {
    if (scaleX < 0 || scaleY < 0) error("Negative scale are unsupported")
    if (scaleX < 0.0001 || scaleY < 0.0001) return
    if (anchorX !in 0.0..1.0) error("Anchor is relative so it must be in 0..1 range")
    if (anchorY !in 0.0..1.0) error("Anchor is relative so it must be in 0..1 range")
    val img = src.transform {
        scale(scaleX.toDouble(), scaleY.toDouble())
        rotate(rotation.toDouble(), src.width / 2.0, src.height / 2.0)
    }
    val x = startX - (img.width * anchorX).toInt()
    val y = startY - (img.height * anchorY).toInt()
    for (dx in 0 until img.width) {
        for (dy in 0 until img.height) {
            val setX = (x + dx).takeIf { it in 0 until width } ?: continue
            val setY = (y + dy).takeIf { it in 0 until height } ?: continue
            val rgb = img.getRGB(dx, dy).takeIf { (it shr 24) and 0xFF > 127 } ?: continue
            setRGB(setX, setY, rgb)
        }
    }
}

inline fun BufferedImage.transform(transform: AffineTransform.() -> Unit): BufferedImage =
    AffineTransformOp(
        AffineTransform().apply(transform),
        AffineTransformOp.TYPE_BICUBIC
    ).filter(this, null)


inline fun <T : Any> runOrNull(block: () -> T): T? = runCatching(block).getOrNull()

val windowState = WindowState(
    size = DpSize(
        width = 1200.dp, height = 600.dp
    )
)

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
val taskPool = newSingleThreadContext("App pool")

@OptIn(ExperimentalCoroutinesApi::class)
val tasks = CoroutineScope(taskPool)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class, ExperimentalComposeUiApi::class)
fun main() = application {
    var path by remember { mutableStateOf(File.listRoots()?.firstOrNull()?.absolutePath ?: "/") }
    var outDir by remember { mutableStateOf(File(path, "out").absolutePath) }
    var outName by remember { mutableStateOf("generated.png") }

    val readImgs = remember { mutableStateListOf<BufferedImage>() }

    var scale by remember { mutableFloatStateOf(1f) }
    var density by remember { mutableFloatStateOf(.5f) }
    var angleDeviation by remember { mutableFloatStateOf(0f) }
    var sizeDeviation by remember { mutableIntStateOf(0) }
    var fileReading: Job by remember { mutableStateOf(tasks.launch {}) }
    var generating: Job by remember { mutableStateOf(tasks.launch {}) }
    var renderSize: IntSize? by remember { mutableStateOf(null) }
    var saving: Job by remember { mutableStateOf(tasks.launch {}) }
    var textW by remember { mutableStateOf("1000") }
    var textH by remember { mutableStateOf("1000") }
    var picking by remember { mutableStateOf(Picking.None) }
    AppTheme(darkTheme = false) {
        Window(
            title = "BackFiller", state = windowState, onCloseRequest = {
                tasks.cancel()
                taskPool.close()
                exitApplication()
            }) {
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    TextField(
                        path,
                        {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Input image(s)") })
                    Row {
                        Button(
                            { picking = Picking.FileIn }, modifier = Modifier.weight(1f).padding(10.dp)
                        ) { Text("Select file(s)") }
                        Button(
                            { picking = Picking.DirIn }, modifier = Modifier.weight(1f).padding(10.dp)
                        ) { Text("Select a directory") }
                    }
                    ParamSlider(
                        label = "Scale",
                        value = scale,
                        valueRange = 0.1f..2f,
                        onValueChange = { scale = it },
                        indicator = scale.toString()
                    )
                    ParamSlider(
                        label = "Density",
                        value = density,
                        valueRange = .1f..0.9f,
                        onValueChange = { density = it },
                        indicator = density.toString()
                    )
                    ParamSlider(
                        label = "Angle deviation",
                        value = angleDeviation,
                        valueRange = 0f..(PI * 2).toFloat(),
                        onValueChange = { angleDeviation = it },
                        indicator = "%.2f°".format(Math.toDegrees(angleDeviation.toDouble()))
                    )
                    ParamSlider(
                        label = "Size deviation",
                        value = sizeDeviation.toFloat(),
                        valueRange = 0f..50f,
                        steps = 50,
                        onValueChange = { sizeDeviation = it.toInt() },
                        indicator = "±$sizeDeviation%"
                    )
                    Row {
                        TextField(
                            textW, { textW = it },
                            modifier = Modifier.weight(1f),
                            isError = textW.toPositiveIntOrNull() == null,
                            label = { Text("Image width") })
                        TextField(
                            textH, { textH = it },
                            modifier = Modifier.weight(1f),
                            isError = textH.toPositiveIntOrNull() == null,
                            label = { Text("Image height") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button({
                            renderSize?.let { (w, h) ->
                                randomize(density, w, h, readImgs, random)
                            }
                        }, modifier = Modifier.weight(1f).padding(10.dp)) {
                            Text("Generate")
                        }
                        Button(
                            { generating.cancel("Generating cancelled") }, modifier = Modifier.weight(1f).padding(10.dp)
                        ) {
                            Text("Cancel")
                        }
                    }
                    Column {
                        Row {
                            TextField(
                                outDir,
                                {},
                                readOnly = true,
                                label = { Text("Output directory") },
                                modifier = Modifier.weight(1f)
                            )
                            TextField(
                                outName,
                                { outName = it },
                                label = { Text("Generated image name") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row {
                            Button(
                                { picking = Picking.DirOut }, modifier = Modifier.weight(1f).padding(10.dp)
                            ) { Text("Select output directory") }
                            Button(modifier = Modifier.weight(1f).padding(10.dp), onClick = {
                                saving.cancel("Restarting Save")
                                saving = tasks.launch {
                                    val resW = textW.toIntOrNull() ?: return@launch
                                    val resH = textH.toIntOrNull() ?: return@launch
                                    renderSize?.let { (w, h) ->
                                        val scW = resW / w.toFloat()
                                        val scH = resH / h.toFloat()
                                        val res = generateImage(
                                            resW,
                                            resH,
                                            scale * scW,
                                            scale * scH,
                                            angleDeviation,
                                            sizeDeviation
                                        )

                                        val file = File(outDir, outName)
                                        file.mkdirs()
                                        file.createNewFile()
                                        ImageIO.write(res, file.extension, file)
                                    }
                                }
                            }) {
                                Text("Save")
                            }
                        }
                    }
                }
                val w = textW.toPositiveIntOrNull()?.toFloat() ?: 1f
                val h = textH.toPositiveIntOrNull() ?: 1
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .aspectRatio(w / h, true)
                        .weight(1f)
                        .fillMaxSize()
                        .padding(10.dp)
                        .onGloballyPositioned { renderSize = it.size }
                        .border(
                            5.dp,
                            MaterialTheme.colorScheme.tertiary
                        ).clipToBounds(),
                    contentAlignment = Alignment.TopStart
                ) {
                    renderSize?.let { ImagePreview(it, scale, angleDeviation, sizeDeviation) }
                    if (saving.isActive)
                        CircularProgressIndicator(
                            Modifier.align(Alignment.Center).size(100.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            strokeWidth = 10.dp
                        )
                }
            }
        }
        MultipleFilePicker(picking == Picking.FileIn, path, listOf("png, jpeg", "webp"), "Select a picture") { list ->
            list?.let { files ->
                path = files.joinToString(", ") { File(it.path).name }
                fileReading.cancel()
                fileReading = tasks.launch {
                    readImgs.clear()
                    readImgs.addAll(files.mapNotNull { file ->
                        runOrNull { ImageIO.read(File(file.path)) }
                    })
                }
            }
            picking = Picking.None
        }
        DirectoryPicker(picking == Picking.DirIn, path, "Select a directory") { p ->
            p?.let {
                path = it
                fileReading.cancel()
                fileReading = tasks.launch {
                    readImgs.clear()
                    readImgs.addAll(
                        File(path).listFiles()?.filterNotNull()?.filter(File::isFile)
                            ?.mapNotNull { runOrNull { ImageIO.read(it) } } ?: emptyList())
                }
            }
            picking = Picking.None
        }
        DirectoryPicker(picking == Picking.DirOut, outDir, "Select output folder") { p ->
            p?.let { outDir = it }
            picking = Picking.None
        }
    }
}

enum class FocusMode {
    None, Focus, Rotation
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.ImagePreview(
    size: IntSize,
    scale: Float,
    rotation: Float,
    sizeDeviation: Int,
) {
    for (element in elements) {
        var x by element.x
        var y by element.y
        var sizeDeviationPower by element.sizeDeviationPower
        var anglePower by element.anglePower
        var focusMode by remember { mutableStateOf(FocusMode.None) }

        val img = element.image.transform {
            val sc = scale + scale * sizeDeviationPower * (sizeDeviation / 100f)
            scale(sc.toDouble(), sc.toDouble())
            rotate((rotation * anglePower).toDouble(), element.image.width / 2.0, element.image.height / 2.0)
        }

        Image(
            img.toComposeImageBitmap(), null,
            Modifier
                .absoluteOffset {
                    IntOffset(
                        x = (size.width * x - (img.width / 2)).toInt(),
                        y = (size.height * y - (img.height / 2)).toInt()
                    )
                }
                .onPointerEvent(PointerEventType.Scroll) {
                    when (focusMode) {
                        FocusMode.Focus -> {
                            sizeDeviationPower =
                                (sizeDeviationPower - it.changes.fold(0f) { acc, n -> acc + n.scrollDelta.y } / 50).coerceIn(
                                    (-1f)..1f
                                )
                        }

                        FocusMode.Rotation -> {
                            anglePower =
                                (anglePower - it.changes.fold(0f) { acc, n -> acc + n.scrollDelta.y } / 50).coerceIn(0f..1f)
                        }

                        else -> Unit
                    }

                }
                .onPointerEvent(PointerEventType.Enter) {
                    focusMode = FocusMode.Focus
                }
                .onPointerEvent(PointerEventType.Exit) {
                    focusMode = FocusMode.None
                }
                .border(
                    2.dp,
                    when (focusMode) {
                        FocusMode.None -> Color.Transparent
                        FocusMode.Focus -> Color.Black
                        FocusMode.Rotation -> Color.Red
                    }
                )
                .onClick(
                    matcher = PointerMatcher.mouse(PointerButton.Secondary),
                    onClick = {
                        focusMode =
                            if (focusMode == FocusMode.Focus)
                                FocusMode.Rotation
                            else FocusMode.Focus
                    }
                )
                .onDrag { (dx, dy) ->
                    x += dx / size.width
                    y += dy / size.height
                }
        )
    }
}

@Composable
fun ParamSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    indicator: String,
): Unit = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        label,
        modifier = Modifier.weight(SLIDER_LABEL_WEIGHT),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.primary
    )
    Slider(
        value, onValueChange, steps = steps, valueRange = valueRange, modifier = Modifier.weight(SLIDER_WEIGHT)
    )
    Text(
        indicator,
        Modifier.weight(SLIDER_VALUE_WEIGHT),
        maxLines = 1,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.primary
    )
}