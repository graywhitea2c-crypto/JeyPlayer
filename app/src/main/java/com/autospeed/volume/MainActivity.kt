package com.autospeed.volume

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val Bg = Color(0xFF050810)
private val Panel = Color(0xFF0B1422)
private val Teal = Color(0xFF00E5FF)
private val NeonBlue = Color(0xFF287BFF)
private val Muted = Color(0xFF91A4BA)
private val Ink = Color(0xFFF4F8FF)

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // The dashboard remains available; the service checks permission before starting location updates.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = AppPrefs.prefs(this)
        if (!p.contains(AppPrefs.MIN_VOLUME)) p.edit().putInt(AppPrefs.MIN_VOLUME, 20).apply()
        if (!p.contains(AppPrefs.MAX_VOLUME)) p.edit().putInt(AppPrefs.MAX_VOLUME, 75).apply()
        if (!p.contains(AppPrefs.MAX_SPEED)) p.edit().putInt(AppPrefs.MAX_SPEED, 120).apply()

        val required = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) required += Manifest.permission.POST_NOTIFICATIONS
        if (required.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            permissions.launch(required.toTypedArray())
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Teal, secondary = NeonBlue, background = Bg, surface = Panel)) {
                Dashboard(
                    onStart = {
                        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (fine || coarse) {
                            p.edit().putBoolean(AppPrefs.IS_RUNNING, true).apply()
                            ContextCompat.startForegroundService(this, Intent(this, SpeedVolumeService::class.java).setAction("START"))
                        } else {
                            permissions.launch(required.toTypedArray())
                        }
                    },
                    onStop = {
                        stopService(Intent(this, SpeedVolumeService::class.java))
                        p.edit().putBoolean(AppPrefs.IS_RUNNING, false).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun Dashboard(onStart: () -> Unit, onStop: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPrefs.prefs(context) }
    var running by remember { mutableStateOf(prefs.getBoolean(AppPrefs.IS_RUNNING, false)) }
    var speed by remember { mutableIntStateOf(prefs.getInt(AppPrefs.CURRENT_SPEED, 0)) }
    var percent by remember { mutableIntStateOf(prefs.getInt(AppPrefs.CURRENT_PERCENT, 20)) }
    var minVol by remember { mutableFloatStateOf(prefs.getInt(AppPrefs.MIN_VOLUME, 20).toFloat()) }
    var maxVol by remember { mutableFloatStateOf(prefs.getInt(AppPrefs.MAX_VOLUME, 75).toFloat()) }
    var maxSpeed by remember { mutableFloatStateOf(prefs.getInt(AppPrefs.MAX_SPEED, 120).toFloat()) }

    LaunchedEffect(Unit) {
        while (true) {
            running = prefs.getBoolean(AppPrefs.IS_RUNNING, false)
            speed = prefs.getInt(AppPrefs.CURRENT_SPEED, 0)
            percent = prefs.getInt(AppPrefs.CURRENT_PERCENT, minVol.toInt())
            delay(700)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF071D2B)).border(1.dp, Teal.copy(alpha = .65f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Text("♫", color = Teal, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("AUTO", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text("SPEED", color = Teal, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, modifier = Modifier.offset(y = (-5).dp))
                    Text("کنترل هوشمند صدای خودرو", color = Muted, fontSize = 12.sp)
                }
                Text(if (running) "● فعال" else "● آماده", color = if (running) Teal else Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("وضعیت سرویس", if (running) "فعال" else "متوقف", if (running) Teal else Muted, Modifier.weight(1f))
                StatCard("صدای رسانه", "$percent٪", Teal, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            CardPanel {
                Text("سرعت فعلی", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$speed", color = Ink, fontSize = 64.sp, fontWeight = FontWeight.Light, lineHeight = 68.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("km/h", color = Teal, fontSize = 17.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(if (running) "سرعت از GPS دریافت می‌شود" else "برای نمایش سرعت واقعی، سرویس را شروع کنید", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { (speed / maxSpeed.coerceAtLeast(1f)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Teal, trackColor = Color(0xFF17283C)
                )
            }
            Spacer(Modifier.height(14.dp))
            CardPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("صدای رسانه", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("سطح صدای رسانهٔ سیستم", color = Muted, fontSize = 12.sp)
                    }
                    Text("$percent٪", color = Teal, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(progress = { (percent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Teal, trackColor = Color(0xFF17283C))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("حداقل ${minVol.toInt()}٪", color = Muted, fontSize = 11.sp)
                    Text("حداکثر ${maxVol.toInt()}٪", color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            CardPanel {
                Text("تنظیمات هوشمند", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("تغییرات ذخیره می‌شوند و سرویس از آن‌ها استفاده می‌کند.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                SettingSlider("حداقل صدا", "${minVol.toInt()}٪", minVol, { minVol = it.coerceAtMost(maxVol - 5); prefs.edit().putInt(AppPrefs.MIN_VOLUME, minVol.toInt()).apply() }, 0f..95f)
                SettingSlider("حداکثر صدا", "${maxVol.toInt()}٪", maxVol, { maxVol = it.coerceAtLeast(minVol + 5); prefs.edit().putInt(AppPrefs.MAX_VOLUME, maxVol.toInt()).apply() }, 5f..100f)
                SettingSlider("سرعت مرجع", "${maxSpeed.toInt()} km/h", maxSpeed, { maxSpeed = it; prefs.edit().putInt(AppPrefs.MAX_SPEED, maxSpeed.toInt()).apply() }, 40f..160f)
            }
            Spacer(Modifier.height(14.dp))
            CardPanel {
                Text("منحنی سرعت و صدا", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("نمایش رابطهٔ خطی بین سرعت و سطح صدا", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                    val left = 8f; val right = size.width - 8f; val top = 8f; val bottom = size.height - 8f
                    for (i in 0..3) {
                        val y = top + (bottom - top) * i / 3
                        drawLine(Color(0xFF29404E), Offset(left, y), Offset(right, y), 1f)
                    }
                    val pts = (0..40).map { i ->
                        val x = left + (right - left) * i / 40
                        val fraction = i / 40f
                        val volumeFraction = (minVol + (maxVol - minVol) * fraction) / 100f
                        Offset(x, bottom - (bottom - top) * volumeFraction)
                    }
                    pts.zipWithNext().forEach { (a, b) -> drawLine(Teal, a, b, 4f) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("۰ km/h", color = Muted, fontSize = 11.sp)
                    Text("${maxSpeed.toInt()} km/h", color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (!running) onStart() else onStop() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (running) Color(0xFF293D4A) else Teal, contentColor = if (running) Ink else Bg),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(if (running) "توقف سرویس خودکار" else "شروع تنظیم خودکار صدا", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text("هنگام رانندگی تنظیمات را انجام ندهید.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier.border(1.dp, tint.copy(alpha = .28f), RoundedCornerShape(18.dp)), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(7.dp))
            Text(value, color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CardPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Teal.copy(alpha = .20f), RoundedCornerShape(22.dp))) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun SettingSlider(label: String, valueText: String, value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Ink, fontSize = 13.sp)
            Text(valueText, color = Teal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = Teal, activeTrackColor = Teal, inactiveTrackColor = Color(0xFF314653)))
    }
}
