package ao.creativespace.englishcourse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG

@Composable
internal fun IonIcon(
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF6F7782),
    size: Dp = 22.dp
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val svgPicture = remember(name, tint) {
        runCatching {
            val content = context.assets.open("ionicons/$name.svg").bufferedReader().use { it.readText() }
            val hex = "#" + String.format(
                java.util.Locale.US,
                "%02X%02X%02X",
                (tint.red * 255).toInt().coerceIn(0, 255),
                (tint.green * 255).toInt().coerceIn(0, 255),
                (tint.blue * 255).toInt().coerceIn(0, 255)
            )
            SVG.getFromString(content.replace("currentColor", hex).replace("#000000", hex).replace("#000", hex)).renderToPicture()
        }.getOrNull()
    }

    Canvas(modifier = modifier.size(size)) {
        val picture = svgPicture ?: return@Canvas
        val canvas = drawContext.canvas.nativeCanvas
        val save = canvas.save()
        canvas.scale(this.size.width / picture.width.toFloat(), this.size.height / picture.height.toFloat())
        picture.draw(canvas)
        canvas.restoreToCount(save)
    }
}
