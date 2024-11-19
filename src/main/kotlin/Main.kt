import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
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

@Immutable
data class Element(
    val x: Int, val y: Int, val anglePower: Float, val sizeDeviationPower: Float, val image: BufferedImage
)

var elements = emptyList<Element>()

fun randomize(density: Float, w: Int, h: Int, images: List<BufferedImage>, random: Random) {
    val stepX = (1 / density).toInt() * STEP_MULTIPLIER
    val stepY = (1 / density).toInt() * STEP_MULTIPLIER
    elements =
        if (images.isEmpty()) emptyList()
        else (0..<w step stepX).flatMap { x ->
            (0..<h step stepY).map { y ->
                val nx = x + random.nextInt((-stepX / 2)..(stepX / 2))
                val ny = y + random.nextInt((-stepY / 2)..(stepY / 2))
                Element(
                    nx,
                    ny,
                    anglePower = random.nextFloat(),
                    sizeDeviationPower = (random.nextFloat() * 2) - 1,
                    image = images.random(random)
                )
            }
        }
}

fun generateImage(
    w: Int,
    h: Int,
    size: Float,
    rotation: Float,
    sizeDeviation: Int,
): BufferedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
    elements.forEach { (x, y, anglePower, sizeDeviationPower, image) ->
        val ds = size + size * sizeDeviationPower * (sizeDeviation / 100f)
        draw(
            image,
            x,
            y,
            (image.width * ds).toInt(),
            (image.height * ds).toInt(),
            rotation * anglePower,
            .5,
            .5,
        )
    }
}

fun BufferedImage.draw(
    src: BufferedImage,
    startX: Int,
    startY: Int,
    w: Int,
    h: Int,
    rotation: Float,
    anchorX: Double = 0.0,
    anchorY: Double = 0.0,
) {
    if (w == 0 || h == 0) return
    if (w < 0 || h < 0) error("Negative w or h are unsupported")
    if (anchorX !in 0.0..1.0) error("Anchor is relative so it must be in 0..1 range")
    if (anchorY !in 0.0..1.0) error("Anchor is relative so it must be in 0..1 range")
    val x = startX - (w * anchorX).toInt()
    val y = startY - (h * anchorY).toInt()
    val img = src.rotate(rotation.toDouble())
    for (dx in 0 until w) {
        for (dy in 0 until h) {
            val setX = (x + dx).takeIf { it in 0 until width } ?: continue
            val setY = (y + dy).takeIf { it in 0 until height } ?: continue
            val getX = (img.width.toDouble() / w * dx).takeIf { it < img.width }?.toInt() ?: continue
            val getY = (img.height.toDouble() / h * dy).takeIf { it < img.height }?.toInt() ?: continue
            val rgb = img.getRGB(getX, getY).takeIf { (it shr 24) and 0xFF > 127 } ?: continue
            setRGB(setX, setY, rgb)
        }
    }
}

fun BufferedImage.rotate(radians: Double): BufferedImage = AffineTransformOp(AffineTransform().apply {
    translate(width / 2.0, height / 2.0)
    rotate(radians)
    translate(-width / 2.0, -height / 2.0)
}, AffineTransformOp.TYPE_BICUBIC).filter(this, null)

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
fun main() = application {
    var path by remember { mutableStateOf(File.listRoots()?.firstOrNull()?.absolutePath ?: "/") }
    var outDir by remember { mutableStateOf(File(path, "out").absolutePath) }
    var outName by remember { mutableStateOf("generated.png") }

    val readImgs = remember { mutableStateListOf<BufferedImage>() }

    var size by remember { mutableFloatStateOf(1f) }
    var density by remember { mutableFloatStateOf(.5f) }
    var angleDeviation by remember { mutableFloatStateOf(0f) }
    var sizeDeviation by remember { mutableIntStateOf(0) }
    var fileReading: Job by remember { mutableStateOf(tasks.launch {}) }
    var generating: Job by remember { mutableStateOf(tasks.launch {}) }
    var saving: Job = remember { tasks.launch {} }
    var resW by remember { mutableIntStateOf(1000) }
    var resH by remember { mutableIntStateOf(1000) }
    var res by remember {
        mutableStateOf(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
    }
    var picking by remember { mutableStateOf(Picking.None) }
    fun regen() {
        generating.cancel("Regenerating")
        generating = tasks.launch(taskPool) {
            res = generateImage(resW, resH, size, angleDeviation, sizeDeviation)
        }
    }
    AppTheme(darkTheme = false) {
        Window(
            title = "BackFiller", onCloseRequest = ::exitApplication, state = windowState
        ) {
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
                        label = "Size",
                        value = size,
                        valueRange = 0f..2f,
                        onValueChange = { size = it; regen() },
                        indicator = size.toString()
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
                        onValueChange = { angleDeviation = it; regen() },
                        indicator = "±%.2f°".format(Math.toDegrees(angleDeviation.toDouble()))
                    )
                    ParamSlider(
                        label = "Size deviation",
                        value = sizeDeviation.toFloat(),
                        valueRange = 0f..50f,
                        steps = 50,
                        onValueChange = { sizeDeviation = it.toInt(); regen() },
                        indicator = "±$sizeDeviation%"
                    )
                    Row {
                        TextField(
                            resW.toString(),
                            { resW = it.filter(Char::isDigit).toIntOrNull() ?: resW },
                            modifier = Modifier.weight(1f),
                            label = { Text("Image width") })
                        TextField(
                            resH.toString(),
                            { resH = it.filter(Char::isDigit).toIntOrNull() ?: resH },
                            modifier = Modifier.weight(1f),
                            label = { Text("Image height") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button({
                            randomize(density, resW, resH, readImgs, random)
                            regen()
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
                                val file = File(outDir, outName)
                                file.mkdirs()
                                file.createNewFile()
                                saving.cancel("Restarting saving")
                                saving = tasks.launch(taskPool) {
                                    ImageIO.write(res, file.extension, file)
                                }
                            }) {
                                Text("Save")
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).padding(10.dp), contentAlignment = Alignment.Center
                ) {
                    Image(
                        res.toComposeImageBitmap(),
                        "Generated Image",
                        modifier = Modifier.fillMaxSize().border(5.dp, color = MaterialTheme.colorScheme.secondary)
                    )/*if (generating.isActive)
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.tertiary,
                            strokeWidth = 10.dp,
                            modifier = Modifier.size(100.dp)
                        )*/
                }
            }
        }
        MultipleFilePicker(picking == Picking.FileIn, path, listOf("png, jpeg"), "Select a picture") { list ->
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