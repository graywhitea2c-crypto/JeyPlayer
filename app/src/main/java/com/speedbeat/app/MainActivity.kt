package com.speedbeat.app

import android.Manifest
import android.content.Context
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

    fun startGps(onSpeed: (Float) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = LocationListener { loc: Location ->
                if (loc.hasSpeed()) onSpeed((loc.speed * 3.6f).coerceIn(0f, 240f))
            }
            locationListener = listener
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener)
        } catch (_: SecurityException) { }
        catch (_: IllegalArgumentException) { }
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
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionGranted = result.values.any { it }
        if (permissionGranted) {
            gpsEnabled = true
            activity.startGps { speed = it }
        }
    }

    LaunchedEffect(speed, sensitivity, gpsEnabled) {
        if (gpsEnabled) {
            bpm = (75 + speed * 1.65f * sensitivity).toInt().coerceIn(75, 190)
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
                    Button(onClick = {
                        if (!permissionGranted) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        else {
                            gpsEnabled = !gpsEnabled
                            if (gpsEnabled) activity.startGps { speed = it } else activity.stopGps()
                        }
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (gpsEnabled) Color(0xFF285C55) else Color(0xFF33354A))) {
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
    @Volatile private var track: AudioTrack? = null
    @Volatile private var worker: Thread? = null

    fun setBpm(value: Int) { bpm.set(value.coerceIn(60, 200)) }
    fun setVolume(value: Float) { volume.set(value.coerceIn(0f, 1f)) }
    fun setStyle(isAmbient: Boolean) { ambient.set(isAmbient) }
    fun trigger(pad: Int, vol: Float) { padQueue.offer(pad to vol.coerceIn(0f, 1f)) }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        worker = Thread {
            val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
            val audio = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(minBytes * 2)
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            track = audio
            val frameCount = 512
            val buffer = ShortArray(frameCount)
            var phase = 0.0
            var beatFrames = 0L
            var samplesUntilBeat = 0
            var kickEnv = 0.0
            var snareEnv = 0.0
            var hatEnv = 0.0
            var bassPhase = 0.0
            try {
                audio.play()
                while (running.get()) {
                    val currentBpm = bpm.get()
                    val interval = (sampleRate * 60.0 / currentBpm).toInt().coerceAtLeast(1)
                    val queued = padQueue.poll()
                    if (queued != null) {
                        when (queued.first) {
                            0 -> kickEnv = 1.0
                            1, 2 -> snareEnv = 1.0
                            3 -> hatEnv = 1.0
                            4 -> bassPhase = 0.001
                            5 -> { snareEnv = .6; hatEnv = .7 }
                        }
                    }
                    for (i in 0 until frameCount) {
                        if (samplesUntilBeat <= 0) {
                            if (ambient.get()) {
                                if (beatFrames % 4L == 0L) bassPhase = 0.001
                            } else {
                                when ((beatFrames % 4L).toInt()) {
                                    0, 2 -> kickEnv = 1.0
                                    1, 3 -> if (Random.nextFloat() > .3f) hatEnv = .65
                                }
                                if (beatFrames % 4L == 2L) snareEnv = .75
                            }
                            beatFrames++
                            samplesUntilBeat = interval
                        }
                        val t = phase / sampleRate
                        val kick = sin(2.0 * PI * (48.0 + 80.0 * kickEnv) * t) * kickEnv
                        val snare = (Random.nextDouble() * 2 - 1) * snareEnv
                        val hat = (Random.nextDouble() * 2 - 1) * hatEnv
                        val bass = sin(bassPhase * 2.0 * PI) * if (bassPhase > 0) .25 else 0.0
                        val ambientTone = if (ambient.get()) sin(2.0 * PI * 220.0 * t) * .08 else 0.0
                        val sample = ((kick * .8 + snare * .35 + hat * .15 + bass + ambientTone) * volume.get() * 26000).toInt()
                        buffer[i] = sample.coerceIn(-32768, 32767).toShort()
                        phase += 1.0
                        samplesUntilBeat--
                        kickEnv *= .9992
                        snareEnv *= .996
                        hatEnv *= .985
                        if (bassPhase > 0) bassPhase += 55.0 / sampleRate
                    }
                    audio.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                }
            } catch (_: Exception) {
            } finally {
                try { audio.pause(); audio.flush(); audio.release() } catch (_: Exception) { }
                track = null
            }
        }.apply { name = "SpeedBeat-Audio"; priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        running.set(false)
        try { worker?.join(350) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        worker = null
    }
}
