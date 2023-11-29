import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.random.Random

const val POPCORN = "C:\\Users\\Markn\\Desktop\\Projects\\popcorn"

object Noise {
    private val noise = mutableMapOf<Pair<Int, Int>, Double>()
    operator fun get(x: Int, y: Int): Double =
        noise.getOrPut(x to y) {
            Perlin.noise(x.toDouble(), y.toDouble())
        }

    fun clear() {
        noise.clear()
    }
}

fun generateRandomImage(fill: List<BufferedImage>, w: Int, h: Int, size: Float, density: Float, smart: Boolean): BufferedImage =
    BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
        for (x in 0 until w) {
            for (y in 0 until h) {
                if ((if (smart) Noise[x, y] else Random.nextDouble()) < density) {
                    fill.randomOrNull()?.let { img ->
                        draw(img, x, y, (img.width * size).toInt(), (img.height * size).toInt(), .5, .5)
                    }
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
    var path by remember { mutableStateOf(POPCORN) }
    var outPath by remember { mutableStateOf("") }
    var readImgs: List<BufferedImage> by remember { mutableStateOf(emptyList()) }
    var sizeMul by remember { mutableStateOf(1f) }
    var density by remember { mutableStateOf(.5f) }
    var fileReading: Job? by remember { mutableStateOf(null) }
    var generating: Thread? by remember { mutableStateOf(null) }
    var resW by remember { mutableStateOf(1000) }
    var resH by remember { mutableStateOf(1000) }
    var status by remember { mutableStateOf("") }
    var smart by remember { mutableStateOf(true) }
    var res by remember {
        mutableStateOf(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
    }

    fun regen() {
        generating?.interrupt()
        status = "Generating"
        generating = thread {
            res = generateRandomImage(readImgs, resW, resH, sizeMul, density, smart)
            status = ""
        }
    }
    LaunchedEffect(path) {
        fileReading?.cancelAndJoin()
        val file = File(path)
        fileReading = if (file.isDirectory)
            launch {
                readImgs =
                    file.listFiles()?.filterNotNull()?.mapNotNull { runOrNull { ImageIO.read(it) } }
                        ?: emptyList<BufferedImage>().also { println("Failed to read $path") }
            }
        else
            launch {
                readImgs =
                    listOfNotNull(runOrNull { ImageIO.read(file) }.also { if (it == null) println("Failed to read $path") })
            }
    }
    Window(onCloseRequest = ::exitApplication) {
        Row {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                TextField(path, { path = it }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth()) {
                    Text("Size", modifier = Modifier.weight(1f))
                    Slider(sizeMul, { sizeMul = it }, valueRange = 0f..10f, modifier = Modifier.weight(5f))
                    TextField(sizeMul.toString(), { sizeMul = it.toFloatOrNull() ?: sizeMul }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("Density", modifier = Modifier.weight(1f))
                    Slider(density, { density = it }, valueRange = 0f..1f, modifier = Modifier.weight(5f))
                    TextField(density.toString(), { density = it.toFloatOrNull() ?: density }, Modifier.weight(1f))
                }
                Row {
                    TextField(
                        resW.toString(),
                        { resW = it.filter(Char::isDigit).toIntOrNull() ?: resW },
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        resH.toString(),
                        { resH = it.filter(Char::isDigit).toIntOrNull() ?: resH },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row {
                    Button({ regen() }) {
                        Text("(Re)Generate")
                    }
                    Text("Smart")
                    Switch(smart, {smart = it})
                    Button({ Noise.clear(); regen() }) {
                        Text("Regenerate Noise")
                    }
                    Button({ generating?.interrupt(); status = "" }) {
                        Text("Cancel")
                    }
                    Text(status)
                }
                Row {
                    TextField(outPath, { outPath = it })
                    Button({
                        val file = File(outPath)
                        file.mkdirs()
                        file.createNewFile()
                        ImageIO.write(res, "png", file)
                    }) {
                        Text("Save")
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Image(res.toComposeImageBitmap(), "Generated Image", modifier = Modifier.fillMaxSize())
            }
        }
    }
}