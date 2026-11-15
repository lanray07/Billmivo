package com.billmivo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

data class BillPalette(
    val canvas: Color, val paper: Color, val ink: Color, val muted: Color, val line: Color,
    val green: Color, val mint: Color, val gold: Color, val amberText: Color, val red: Color,
    val redPaper: Color, val chartHistory: Color
)

private val lightPalette = BillPalette(
    Color(0xFFF6F5EF), Color(0xFFFFFEFA), Color(0xFF202E28), Color(0xFF52635B), Color(0xFFDCE2D8),
    Color(0xFF236054), Color(0xFFE8F0DF), Color(0xFFF7EDD7), Color(0xFF735115), Color(0xFF883A2C), Color(0xFFF9ECE5),
    Color(0xFF6E8975)
)
private val darkPalette = BillPalette(
    Color(0xFF101B17), Color(0xFF192722), Color(0xFFF3F3E8), Color(0xFFBCCBC2), Color(0xFF3A5046),
    Color(0xFFADE0BE), Color(0xFF253F32), Color(0xFF46381F), Color(0xFFF7D99F), Color(0xFFFFB7A6), Color(0xFF412820),
    Color(0xFF69967A)
)
private val LocalPalette = staticCompositionLocalOf { lightPalette }
val palette: BillPalette @Composable get() = LocalPalette.current
val forest = Color(0xFF143C34)
val lime = Color(0xFFD6EDB7)
val pale = Color(0xFFF6FAEB)

@Composable
fun BillmivoTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) darkPalette else lightPalette
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.green, onPrimary = if (dark) forest else Color.White,
        primaryContainer = p.mint, onPrimaryContainer = p.ink,
        secondaryContainer = p.mint, onSecondaryContainer = p.ink,
        background = p.canvas, onBackground = p.ink,
        surface = p.paper, onSurface = p.ink, surfaceVariant = p.mint, onSurfaceVariant = p.muted,
        surfaceContainer = p.paper, surfaceContainerHigh = p.paper, surfaceContainerHighest = p.paper,
        outline = p.muted, outlineVariant = p.line, error = p.red, errorContainer = p.redPaper
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 37.sp, lineHeight = 42.sp),
            headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 32.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
        ), content = content)
    }
}

@Composable
fun BrandBar(onAdd: () -> Unit, onVoice: () -> Unit) {
    Surface(color = palette.canvas) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(painterResource(R.drawable.billmivo_mark), null, Modifier.size(36.dp), tint = Color.Unspecified)
            Text(stringResource(R.string.app_name), Modifier.weight(1f),
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = palette.ink)
            IconButton(onClick = onVoice, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.voice), tint = palette.green)
            }
            Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_add), null, Modifier.size(19.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.add_bill))
            }
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.background(palette.paper, shape).border(1.dp, palette.line, shape)
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = palette.ink)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.muted) }
    }
}

@Composable
fun <T> MenuField(label: String, selected: T, options: List<T>, format: @Composable (T) -> String,
                  onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("$label · ${format(selected)}")
            Icon(painterResource(R.drawable.ic_expand), null, Modifier.size(20.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { item ->
                DropdownMenuItem(text = { Text(format(item)) }, onClick = { expanded = false; onSelect(item) })
            }
        }
    }
}

@Composable
fun displayLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.UK
