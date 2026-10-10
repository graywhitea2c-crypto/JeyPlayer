package com.speedbeat.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MainActivity : ComponentActivity() {
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpeedBeatApp(this) }
    }

    fun startGps(onSpeed: (Float) -> Unit, onStatus: (String) -> Unit = {}) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            onStatus("مجوز مکان لازم است؛ دکمه اتصال GPS را بزن")
            return
        }
        try {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gpsOn = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            if (!gpsOn) {
                onStatus("مکان گوشی خاموش است؛ Location را از تنظیمات روشن کن")
                return
            }
            val listener = LocationListener { loc: Location ->
                if (loc.hasSpeed()) {
                    val kmh = (loc.speed * 3.6f).coerceIn(0f, 240f)
                    runOnUiThread {
                        onSpeed(kmh)
                        onStatus("GPS متصل است • سرعت در حال به‌روزرسانی")
                    }
                }
            }
            locationListener = listener
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener)
            onStatus("در انتظار دریافت سیگنال GPS… فضای باز کمک می‌کند")
        } catch (_: SecurityException) {
            onStatus("مجوز مکان در دسترس نیست؛ تنظیمات برنامه را بررسی کن")
        } catch (_: IllegalArgumentException) {
            onStatus("ارائه‌دهنده GPS در دسترس نیست؛ Location را روشن کن")
        }
    }

    fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    fun stopGps() {
        try { locationListener?.let { locationManager?.removeUpdates(it) } } catch (_: Exception) { }
        locationListener = null
    }

    override fun onDestroy() {
        stopGps()
        super.onDestroy()
    }
}

private enum class BeatStyle(val label: String) { ELECTRONIC("الکترونیک"), AMBIENT("امبینت") }

@Composable
private fun SpeedBeatApp(activity: MainActivity) {
    val engine = remember { BeatEngine() }
    var playing by remember { mutableStateOf(false) }
    var gpsEnabled by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(0f) }
    var bpm by remember { mutableIntStateOf(100) }
    var volume by remember { mutableFloatStateOf(0.65f) }
    var sensitivity by remember { mutableFloatStateOf(1f) }
    var style by remember { mutableStateOf(BeatStyle.ELECTRONIC) }
    var activePad by remember { mutableIntStateOf(-1) }
    var gpsStatus by remember { mutableStateOf("برای تغییر هوشمند ریتم، GPS را متصل کن") }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val prefs = remember { activity.getSharedPreferences("speedbeat_permissions", Context.MODE_PRIVATE) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        prefs.edit().putBoolean("location_request_made", true).apply()
        if (permissionGranted) {
            gpsEnabled = true
            activity.startGps(
                onSpeed = { speed = it },
                onStatus = { gpsStatus = it }
            )
        } else {
            gpsEnabled = false
            gpsStatus = "مجوز مکان داده نشد؛ دوباره اتصال GPS را بزن یا از تنظیمات فعالش کن"
        }
    }

    fun requestLocationPermissionOrSettings() {
        if (permissionGranted) {
            gpsEnabled = !gpsEnabled
            if (gpsEnabled) {
                activity.startGps(onSpeed = { speed = it }, onStatus = { gpsStatus = it })
            } else {
                activity.stopGps()
                gpsStatus = "ردیابی GPS متوقف است"
            }
            return
        }
        val askedBefore = prefs.getBoolean("location_request_made", false)
        val showRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
            activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (askedBefore && !showRationale) {
            gpsStatus = "مجوز قبلاً رد شده؛ از تنظیمات برنامه مکان را فعال کن"
            activity.openAppSettings()
        } else {
            prefs.edit().putBoolean("location_request_made", true).apply()
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            val askedBefore = prefs.getBoolean("location_request_made", false)
            val showRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (askedBefore && !showRationale) {
                gpsStatus = "برای استفاده از تغییر ریتم با سرعت، مجوز مکان را از تنظیمات برنامه فعال کن"
            } else {
                prefs.edit().putBoolean("location_request_made", true).apply()
                permissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        } else {
            gpsEnabled = true
            activity.startGps(onSpeed = { speed = it }, onStatus = { gpsStatus = it })
        }
    }

    LaunchedEffect(speed, sensitivity, gpsEnabled) {
        if (gpsEnabled) {
            bpm = (88 + speed * 0.62f * sensitivity).toInt().coerceIn(88, 176)
            engine.setBpm(bpm)
        }
    }
    DisposableEffect(Unit) {
        onDispose { engine.stop(); activity.stopGps() }
    }

    val bg = Color(0xFF10111A)
    val panel = Color(0xFF1B1D2B)
    val accent = Color(0xFFB46BFF)
    MaterialTheme(colorScheme = darkColorScheme(
        background = bg, surface = panel, primary = accent,
        onBackground = Color(0xFFF5F3FF), onSurface = Color(0xFFF5F3FF)
    )) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("SpeedBeat", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("ریتمی که با حرکت تو زنده می‌شود", fontSize = 12.sp, color = Color(0xFFB8B6CA))
                    }
                    Text("♫", fontSize = 34.sp, color = accent)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("سرعت", "${speed.toInt()}", "km/h", Modifier.weight(1f), Color(0xFF36D9C5))
                    MetricCard("تمپو", "$bpm", "BPM", Modifier.weight(1f), accent)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { requestLocationPermissionOrSettings() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (gpsEnabled) Color(0xFF285C55) else Color(0xFF33354A))) {
                        Text(if (gpsEnabled) "GPS فعال است" else "اتصال GPS")
                    }
                    Button(onClick = {
                        playing = !playing
                        if (playing) { engine.setStyle(style == BeatStyle.AMBIENT); engine.setVolume(volume); engine.start() }
                        else engine.stop()
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (playing) Color(0xFFB13F65) else accent)) {
                        Text(if (playing) "■ توقف موسیقی" else "▶ شروع موسیقی", color = Color.White)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(gpsStatus, modifier = Modifier.fillMaxWidth(), color = if (gpsEnabled) Color(0xFF36D9C5) else Color(0xFFFFB45C), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("پدهای ضربی", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(9.dp))
                val pads = listOf(
                    Triple("KICK", Color(0xFFFF5D73), 0), Triple("SNARE", Color(0xFFFFA94D), 1),
                    Triple("CLAP", Color(0xFFB46BFF), 2), Triple("HI-HAT", Color(0xFF36D9C5), 3),
                    Triple("BASS", Color(0xFF5C8DFF), 4), Triple("FX", Color(0xFFE96BE8), 5)
                )
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(190.dp), verticalArrangement = Arrangement.spacedBy(9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), userScrollEnabled = false) {
                    items(pads) { pad ->
                        Column(
                            Modifier.fillMaxWidth().height(88.dp).background(if (activePad == pad.third) pad.second.copy(alpha = .45f) else panel, RoundedCornerShape(16.dp))
                                .clickable {
                                    activePad = pad.third
                                    engine.trigger(pad.third, volume)
                                }.padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("●", color = pad.second, fontSize = 22.sp)
                            Text(pad.first, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("سبک موسیقی", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BeatStyle.values().forEach { s ->
                        FilterChip(selected = style == s, onClick = {
                            style = s
                            engine.setStyle(s == BeatStyle.AMBIENT)
                        }, label = { Text(s.label) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                ControlSlider("ولوم خروجی", volume, { volume = it; engine.setVolume(it) }, "کم", "زیاد")
                ControlSlider("حساسیت تغییر ریتم", sensitivity, { sensitivity = it }, "ملایم", "واکنش سریع")
                Spacer(Modifier.weight(1f))
                Text("سرعت GPS فقط هنگام فعال‌بودن این صفحه خوانده می‌شود.", color = Color(0xFF85869B), fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, unit: String, modifier: Modifier, tint: Color) {
    Column(modifier.background(Color(0xFF1B1D2B), RoundedCornerShape(18.dp)).padding(15.dp)) {
        Text(title, color = Color(0xFFB8B6CA), fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = tint)
            Spacer(Modifier.width(5.dp))
            Text(unit, color = Color(0xFFB8B6CA), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun ControlSlider(title: String, value: Float, onChange: (Float) -> Unit, left: String, right: String) {
    Column(Modifier.fillMaxWidth().padding(top = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 13.sp, color = Color.White)
            Text("${(value * 100).toInt()}٪", fontSize = 12.sp, color = Color(0xFFB46BFF))
        }
        Slider(value = value, onValueChange = onChange, valueRange = if (title.startsWith("حساسیت")) 0.4f..1.8f else 0f..1f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, fontSize = 10.sp, color = Color(0xFF85869B))
            Text(right, fontSize = 10.sp, color = Color(0xFF85869B))
        }
    }
}

private class BeatEngine {
    private val sampleRate = 44100
    private val running = AtomicBoolean(false)
    private val bpm = AtomicInteger(100)
    private val volume = AtomicReference(0.65f)
    private val ambient = AtomicBoolean(false)
    private val padQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<Int, Float>>()
    @Volatile private var worker: Thread? = null

    fun setBpm(value: Int) { bpm.set(value.coerceIn(60, 200)) }
    fun setVolume(value: Float) { volume.set(value.coerceIn(0f, 1f)) }
    fun setStyle(isAmbient: Boolean) { ambient.set(isAmbient) }
    fun trigger(pad: Int, vol: Float) { padQueue.offer(pad to vol.coerceIn(0f, 1f)) }

    private data class Voice(
        var type: Int,
        var phase: Double,
        var age: Int,
        val length: Int,
        val amplitude: Double,
        val frequency: Double,
        val noiseSeed: Int
    )

    fun start() {
        if (!running.compareAndSet(false, true)) return
        worker = Thread {
            val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
            val audio = try {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(minBytes * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
            } catch (_: Exception) {
                running.set(false); return@Thread
            }
            val frameCount = 512
            val buffer = ShortArray(frameCount)
            val voices = ArrayList<Voice>()
            var beatPosition = 0.0
            var stepCounter = 0L
            var sampleClock = 0L
            var lastBpm = bpm.get()
            var nextStep = 0L
            val stepSamples = sampleRate * 60.0 / max(60, lastBpm) / 2.0 // eighth-note grid
            var stepRemaining = stepSamples
            var bassNote = 0
            val scale = doubleArrayOf(55.0, 65.41, 73.42, 82.41, 98.0, 110.0, 130.81, 146.83)

            fun addVoice(type: Int, amp: Double, freq: Double = 100.0, durationMs: Int = 180) {
                voices.add(Voice(type, 0.0, 0, sampleRate * durationMs / 1000, amp, freq, Random.nextInt()))
            }

            try {
                audio.play()
                while (running.get()) {
                    val currentBpm = bpm.get().coerceIn(60, 200)
                    val interval = sampleRate * 60.0 / currentBpm / 2.0
                    for (i in 0 until frameCount) {
                        // Trigger a musical eighth-note grid. Even steps are quarter notes.
                        if (stepRemaining <= 0.0) {
                            val step = (stepCounter % 8L).toInt()
                            val barBeat = (stepCounter % 8L).toInt()
                            if (ambient.get()) {
                                // A steady pulse remains audible even at zero movement.
                                if (step % 4 == 0) addVoice(0, .72, 48.0, 220)
                                if (step % 2 == 1) addVoice(2, .28, 900.0, 70)
                                if (step == 0 || step == 4) {
                                    bassNote = (bassNote + if (stepCounter % 16L == 0L) 2 else 1) % scale.size
                                    addVoice(3, .32, scale[bassNote], 420)
                                }
                                addVoice(4, .12, 220.0 + (step % 4) * 55.0, 500)
                            } else {
                                // Punchy electronic groove: kick on beats 1/3, snare on 2/4,
                                // closed hats on every eighth and extra accent on offbeats.
                                if (step == 0 || step == 4) addVoice(0, .95, 48.0, 240)
                                if (step == 2 || step == 6) addVoice(1, .70, 180.0, 190)
                                addVoice(2, if (step % 2 == 1) .30 else .18, if (step % 2 == 1) 950.0 else 720.0, 65)
                                if (step == 0 || step == 3 || step == 4 || step == 7) {
                                    bassNote = (bassNote + 1) % scale.size
                                    addVoice(3, .38, scale[bassNote], 180)
                                }
                                if (step == 7) addVoice(4, .18, 440.0, 140)
                            }
                            stepCounter++
                            stepRemaining = interval
                        }

                        var mix = 0.0
                        val iterator = voices.iterator()
                        while (iterator.hasNext()) {
                            val v = iterator.next()
                            val p = v.age.toDouble() / v.length
                            if (v.age >= v.length) { iterator.remove(); continue }
                            val env = when (v.type) {
                                0 -> (1.0 - p).pow(3.0) // kick transient
                                1 -> (1.0 - p).pow(2.2) // snare body
                                2 -> (1.0 - p).pow(5.0) // crisp hat
                                3 -> (1.0 - p).pow(1.7) // bass
                                else -> (sin(PI * p).coerceAtLeast(0.0)).pow(1.2)
                            }
                            val raw = when (v.type) {
                                0 -> {
                                    val sweep = v.frequency + 100.0 * (1.0 - p)
                                    sin(v.phase) * env
                                }
                                1, 2 -> {
                                    val noise = Random.nextDouble(-1.0, 1.0)
                                    if (v.type == 1) noise * .72 + sin(v.phase) * .28 else noise
                                }
                                3 -> sin(v.phase) * env
                                else -> sin(v.phase) * .65 + sin(v.phase * 1.498) * .35
                            }
                            mix += raw * env * v.amplitude
                            v.phase += 2.0 * PI * v.frequency / sampleRate
                            v.age++
                        }
                        // Soft saturation keeps peaks controlled and adds density.
                        val gain = volume.get().coerceIn(0f, 1f).toDouble()
                        val saturated = kotlin.math.tanh(mix * gain * 1.65)
                        buffer[i] = (saturated * 28000.0).toInt().coerceIn(-32768, 32767).toShort()
                        sampleClock++
                        stepRemaining -= 1.0
                    }
                    audio.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                }
            } catch (_: Exception) {
            } finally {
                try { audio.pause(); audio.flush(); audio.release() } catch (_: Exception) { }
            }
        }.apply { name = "SpeedBeat-Audio"; priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        running.set(false)
        try { worker?.join(500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        worker = null
    }
}

