package tw.toilet.nearby

import android.os.Build
import android.view.Window
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private data class Preset(val name: String, val hue: Float)

private val presets = listOf(
    Preset("天空藍", 195f), Preset("珊瑚橘", 12f),
    Preset("檸檬黃", 48f), Preset("薄荷綠", 154f),
    Preset("葡萄紫", 274f), Preset("蜜桃粉", 332f),
)

private fun hueColor(hue: Float, saturation: Float, brightness: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.coerceIn(0f, 360f), saturation, brightness)))

@Suppress("DEPRECATION")
@Composable
fun AppTheme(mode: Int, hue: Float, window: Window, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val scheme = remember(dark, hue) {
        val primary = hueColor(hue, if (dark) 0.55f else 0.84f, if (dark) 0.91f else 0.72f)
        val onPrimary = if (primary.luminance() > 0.18f) Color.Black else Color.White
        val container = hueColor(hue, if (dark) 0.43f else 0.22f, if (dark) 0.29f else 0.96f)
        val onContainer = if (dark) Color.White else Color(0xFF1E1B22)
        if (dark) darkColorScheme(
            primary = primary, onPrimary = onPrimary, primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = hueColor((hue + 44) % 360, 0.48f, 0.88f),
            secondaryContainer = hueColor((hue + 44) % 360, 0.39f, 0.32f),
            onSecondaryContainer = Color.White,
            background = Color(0xFF12131A), surface = Color(0xFF1C1D26),
            surfaceVariant = Color(0xFF2B2C38), onSurfaceVariant = Color(0xFFE2DFEB),
        ) else lightColorScheme(
            primary = primary, onPrimary = onPrimary, primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = hueColor((hue + 44) % 360, 0.68f, 0.68f),
            secondaryContainer = hueColor((hue + 44) % 360, 0.20f, 0.95f),
            onSecondaryContainer = Color(0xFF202027),
            background = Color(0xFFFAF9FC), surface = Color.White,
            surfaceVariant = Color(0xFFF0EEF4), onSurfaceVariant = Color(0xFF44434B),
        )
    }
    SideEffect {
        window.statusBarColor = scheme.background.toArgb()
        window.navigationBarColor = scheme.background.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = scheme.background,
            contentColor = scheme.onBackground) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    mode: Int, preset: Int, hue: Float, onBack: () -> Unit,
    onModeChange: (Int) -> Unit, onPresetChange: (Int, Float) -> Unit,
    onHueChange: (Float) -> Unit, onHueChangeFinished: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Text("‹", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            }
            Text("設定", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = colors.onSurface)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("顯示模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("系統", "淺色", "深色").forEachIndexed { index, label ->
                            Choice(label, mode == index, onClick = { onModeChange(index) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("主題色彩", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    presets.chunked(2).forEachIndexed { row, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEachIndexed { column, option ->
                                val index = row * 2 + column
                                Choice(option.name, preset == index,
                                    onClick = { onPresetChange(index, option.hue) },
                                    modifier = Modifier.weight(1f), dotColor = hueColor(option.hue, 0.8f, 0.85f))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Choice("自訂色彩", preset == 6, onClick = { onPresetChange(6, hue) },
                        modifier = Modifier.fillMaxWidth(), dotColor = colors.primary)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).background(colors.primary, CircleShape)
                            .border(BorderStroke(2.dp, colors.onSurfaceVariant), CircleShape))
                        Slider(value = hue, onValueChange = onHueChange,
                            onValueChangeFinished = onHueChangeFinished,
                            valueRange = 0f..360f, modifier = Modifier.weight(1f),
                            thumb = {
                                Box(Modifier.size(24.dp).background(colors.primary, CircleShape)
                                    .border(BorderStroke(2.dp, Color.White), CircleShape))
                            },
                            track = {
                                Box(Modifier.fillMaxWidth().height(12.dp).background(
                                    Brush.horizontalGradient((0..6).map { hueColor(it * 60f, 0.85f, 0.9f) }),
                                    CircleShape))
                            },
                        )
                    }
                    Text("拖動滑桿即可即時預覽", style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                }
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("資料來源", style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant)
                Text("政府開放資料平台-臺南市公廁地址",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                Text("https://data.gov.tw/dataset/7005",
                    modifier = Modifier.clickable { uriHandler.openUri("https://data.gov.tw/dataset/7005") },
                    style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Choice(
    title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier,
    dotColor: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.primary else colors.surfaceVariant) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            if (dotColor != null) {
                Box(Modifier.size(16.dp).background(dotColor, CircleShape)
                    .border(1.dp, if (selected) colors.onPrimary else colors.onSurfaceVariant, CircleShape))
                Spacer(Modifier.size(6.dp))
            }
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) colors.onPrimary else colors.onSurfaceVariant)
            if (selected) Text(" ✓", fontWeight = FontWeight.Bold, color = colors.onPrimary)
        }
    }
}
