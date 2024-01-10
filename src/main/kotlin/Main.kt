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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
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
    None,
    File,
    Dir
}

fun generateRandomImage(
    fill: List<BufferedImage>,
    w: Int,
    h: Int,
    size: Float,
    density: Float,
): BufferedImage =
    BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
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
    img: BufferedImage,
    startX: Int, startY: Int,
    w: Int, h: Int,
    anchorX: Double = 0.0,
    anchorY: Double = 0.0
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
    var readImgs: List<BufferedImage> by remember { mutableStateOf(emptyList()) }
    var sizeMul by remember { mutableStateOf(1f) }
    var density by remember { mutableStateOf(.5f) }
    var fileReading: Thread? by remember { mutableStateOf(null) }
    var generating: Thread? by remember { mutableStateOf(null) }
    var saving: Thread? by remember { mutableStateOf(null) }
    var resW by remember { mutableStateOf(1000) }
    var resH by remember { mutableStateOf(1000) }
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
                readImgs = files.mapNotNull { file ->
                    runOrNull { ImageIO.read(File(file.path)) }
                }
            }
        }
        picking = Picking.None
    }
    DirectoryPicker(picking == Picking.Dir, path, "Select a directory") { p ->
        p?.let {
            path = it
            fileReading?.interrupt()
            fileReading = thread {
                readImgs = File(path).listFiles()?.filterNotNull()?.filter(File::isFile)
                    ?.mapNotNull { runOrNull { ImageIO.read(it) } } ?: emptyList()
            }
        }
        picking = Picking.None
    }
    DirectoryPicker(pickingOut, outDir, "Select output folder") { p ->
        p?.let { outDir = it }
        pickingOut = false
    }
    Window(onCloseRequest = ::exitApplication) {
        Row {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                TextField(
                    path,
                    {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Input image(s)") }
                )
                Row {
                    Button({ picking = Picking.File }) { Text("Select file(s)") }
                    Button({ picking = Picking.Dir }) { Text("Select a directory") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", modifier = Modifier.weight(1f))
                    Slider(sizeMul, { sizeMul = it }, valueRange = 0f..2f, modifier = Modifier.weight(5f))
                    Text(sizeMul.toString(), Modifier.weight(1f), maxLines = 1)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Density", modifier = Modifier.weight(1f))
                    Slider(density, { density = it }, valueRange = .1f..0.9f, modifier = Modifier.weight(5f))
                    Text(density.toString(), Modifier.weight(1f), maxLines = 1)
                }
                Row {
                    TextField(
                        resW.toString(),
                        { resW = it.filter(Char::isDigit).toIntOrNull() ?: resW },
                        modifier = Modifier.weight(1f),
                        label = { Text("Image width") }
                    )
                    TextField(
                        resH.toString(),
                        { resH = it.filter(Char::isDigit).toIntOrNull() ?: resH },
                        modifier = Modifier.weight(1f),
                        label = { Text("Image height") }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button({ regen() }) {
                        Text("Generate")
                    }
                    Button({ generating?.interrupt(); status = "Cancelled" }) {
                        Text("Cancel")
                    }
                    Text("Status: $status")
                }
                Column {
                    Row {
                        TextField(outDir, {}, readOnly = true,
                            label = { Text("Output directory") }
                        )
                        TextField(
                            outName, { outName = it },
                            label = { Text("Generated image name") },
                        )
                    }
                    Row {
                        Button({ pickingOut = true }) { Text("Select output directory") }
                        Button({
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
                modifier = Modifier.weight(1f).background(Color.Gray)
            ) {
                Image(
                    res.toComposeImageBitmap(),
                    "Generated Image",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}