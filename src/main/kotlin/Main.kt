import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import com.darkrockstudios.libraries.mpfilepicker.MultipleFilePicker
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.random.nextInt

const val STEP_MULTIPLIER = 20

enum class Picking {
    None, File, Dir
}

fun generateRandomImage(
    fill: List<BufferedImage>,
    w: Int,
    h: Int,
    size: Float,
    density: Float,
): BufferedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
    val stepX = (1 / density).toInt() * STEP_MULTIPLIER
    val stepY = (1 / density).toInt() * STEP_MULTIPLIER
    for (x in 0..<w step stepX) {
        for (y in 0..<h step stepY) {
            val nx = x + Random.nextInt((-stepX / 2)..(stepX / 2))
            val ny = y + Random.nextInt((-stepY / 2)..(stepY / 2))
            //if (Random.nextDouble() < density)
            fill.randomOrNull()?.let { img ->
                draw(img, nx, ny, (img.width * size).toInt(), (img.height * size).toInt(), .5, .5)
            }
        }
    }
}

fun BufferedImage.draw(
    img: BufferedImage, startX: Int, startY: Int, w: Int, h: Int, anchorX: Double = 0.0, anchorY: Double = 0.0
) {
    if (w == 0 || h == 0) return
    if (w < 0 || h < 0) error("Negative w or h are unsupported")
    if (anchorX !in 0.0..1.0) error("Anchor is relative so it must be in 0..1 range")
    if (anchorY !in 0.0..1.0) error("Anchor is relative so it must be in 0..1 range")
    val x = startX - (w * anchorX).toInt()
    val y = startY - (h * anchorY).toInt()
    for (dx in 0 until w) {
        for (dy in 0 until h) {
            val setX = (x + dx).takeIf { it in 0 until width } ?: continue
            val setY = (y + dy).takeIf { it in 0 until height } ?: continue
            val getX = (img.width.toDouble() / w * dx).takeIf { it < img.width }?.toInt() ?: continue
            val getY = (img.height.toDouble() / h * dy).takeIf { it < img.height }?.toInt() ?: continue
            val rgb = img.getRGB(getX, getY).takeIf { (it shr 24) and 0xFF != 0 } ?: continue
            setRGB(setX, setY, rgb)
        }
    }
}

inline fun <T : Any> runOrNull(block: () -> T): T? = try {
    block()
} catch (e: Throwable) {
    null
}

fun main() = application {
    var path by remember { mutableStateOf("C:\\") }
    var outDir by remember { mutableStateOf("") }
    var outName by remember { mutableStateOf("generated.png") }
    val readImgs = remember { mutableStateListOf<BufferedImage>() }
    var sizeMul by remember { mutableFloatStateOf(1f) }
    var density by remember { mutableFloatStateOf(.5f) }
    var fileReading: Thread? by remember { mutableStateOf(null) }
    var generating: Thread? by remember { mutableStateOf(null) }
    var saving: Thread? by remember { mutableStateOf(null) }
    var resW by remember { mutableIntStateOf(1000) }
    var resH by remember { mutableIntStateOf(1000) }
    var status by remember { mutableStateOf("Idle") }
    var res by remember {
        mutableStateOf(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
    }
    var picking by remember { mutableStateOf(Picking.None) }
    var pickingOut by remember { mutableStateOf(false) }

    fun regen() {
        generating?.interrupt()
        status = "Generating..."
        generating = thread {
            res = generateRandomImage(readImgs, resW, resH, sizeMul, density)
            status = "Done"
        }
    }
    MultipleFilePicker(picking == Picking.File, path, listOf("png, jpeg"), "Select a picture") { list ->
        list?.let { files ->
            path = files.joinToString(", ") { File(it.path).name }
            fileReading?.interrupt()
            fileReading = thread {
                readImgs.clear()
                readImgs.addAll(files.mapNotNull { file ->
                    runOrNull { ImageIO.read(File(file.path)) }
                })
            }
        }
        picking = Picking.None
    }
    DirectoryPicker(picking == Picking.Dir, path, "Select a directory") { p ->
        p?.let {
            path = it
            fileReading?.interrupt()
            fileReading = thread {
                readImgs.clear()
                readImgs.addAll(File(path).listFiles()?.filterNotNull()?.filter(File::isFile)
                    ?.mapNotNull { runOrNull { ImageIO.read(it) } } ?: emptyList())
            }
        }
        picking = Picking.None
    }
    DirectoryPicker(pickingOut, outDir, "Select output folder") { p ->
        p?.let { outDir = it }
        pickingOut = false
    }
    Window(
        title = "BackFiller", onCloseRequest = ::exitApplication, state = WindowState(
            size = DpSize(1200.dp, 600.dp)
        )
    ) {
        Row {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                TextField(path,
                    {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Input image(s)") })
                Row {
                    Button({ picking = Picking.File }, modifier = Modifier.weight(1f)) { Text("Select file(s)") }
                    Button({ picking = Picking.Dir }, modifier = Modifier.weight(1f)) { Text("Select a directory") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Slider(sizeMul, { sizeMul = it }, valueRange = 0f..2f, modifier = Modifier.weight(5f))
                    Text(sizeMul.toString(), Modifier.weight(.5f), maxLines = 1, textAlign = TextAlign.Center)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Density", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Slider(density, { density = it }, valueRange = .1f..0.9f, modifier = Modifier.weight(5f))
                    Text(density.toString(), Modifier.weight(.5f), maxLines = 1, textAlign = TextAlign.Center)
                }
                Row {
                    TextField(resW.toString(),
                        { resW = it.filter(Char::isDigit).toIntOrNull() ?: resW },
                        modifier = Modifier.weight(1f),
                        label = { Text("Image width") })
                    TextField(resH.toString(),
                        { resH = it.filter(Char::isDigit).toIntOrNull() ?: resH },
                        modifier = Modifier.weight(1f),
                        label = { Text("Image height") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button({ regen() }, modifier = Modifier.weight(1f)) {
                        Text("Generate")
                    }
                    Text("Status: $status", modifier = Modifier.weight(.5f), textAlign = TextAlign.Center)
                    Button({ generating?.interrupt(); status = "Cancelled" }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                }
                Column {
                    Row {
                        TextField(
                            outDir,
                            {}, readOnly = true,
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
                            { pickingOut = true }, modifier = Modifier.weight(1f)
                        ) { Text("Select output directory") }
                        Button(modifier = Modifier.weight(1f), onClick = {
                            val file = File("$outDir/$outName")
                            file.mkdirs()
                            file.createNewFile()
                            saving?.interrupt()
                            saving = thread {
                                status = "Saving"
                                ImageIO.write(res, file.extension, file)
                                status = "Saved"
                            }
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    res.toComposeImageBitmap(),
                    "Generated Image",
                    modifier = Modifier.fillMaxSize().background(Color.LightGray)
                )
            }
        }
    }
}