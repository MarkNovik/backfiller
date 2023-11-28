import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
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

const val POPCORN = "C:\\Users\\Markn\\Desktop\\Projects\\popcorn\\pop1.png"

@Composable
@Preview
fun App() {
    var path by remember { mutableStateOf(POPCORN) }
    var readImgs: List<BufferedImage> by remember { mutableStateOf(emptyList()) }
    var fileReading: Job? = null
    var resW by remember { mutableStateOf(100) }
    var resH by remember { mutableStateOf(100) }
    var res by remember {
        mutableStateOf(generateRandomImage(resW, resH, readImgs))
    }
    LaunchedEffect(path) {
        fileReading?.cancelAndJoin()
        val file = File(path)
        fileReading = if (file.isDirectory)
            launch { readImgs = file.listFiles()?.filterNotNull()?.mapNotNull { runCatching { ImageIO.read(it) }.getOrNull() } ?: emptyList() }
        else
            launch { readImgs = listOfNotNull(runCatching { ImageIO.read(file) }.getOrNull()) }
    }
    Row {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            TextField(path, { path = it }, modifier = Modifier.fillMaxWidth())
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
            Button({ res = generateRandomImage(resW, resH, readImgs) }) {
                Text("(Re)Generate")
            }
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Image(res.toComposeImageBitmap(), "Generated Image", modifier = Modifier.fillMaxSize())
        }
    }
}

fun generateRandomImage(w: Int, h: Int, fill: List<BufferedImage>): BufferedImage =
    BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
        fill.randomOrNull()?.let { img -> draw(img, width / 2, height / 2, width / 2, height / 2) }
        fill.randomOrNull()?.let { img -> draw(img, width / 2, 0, width / 2, height / 2) }
        fill.randomOrNull()?.let { img -> draw(img, 0, height / 2, width / 2, height / 2) }
        fill.randomOrNull()?.let { img -> draw(img, 0, 0, width / 2, height / 2) }
    }

fun BufferedImage.draw(img: BufferedImage, x: Int, y: Int, w: Int, h: Int) {
    if (w == 0 || h == 0) return
    if (w < 0 || h < 0) error("Negative w or h are unsupported")
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

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}