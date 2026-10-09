package com.example.speedvolumeplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.location.*
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay

fun volumeForSpeed(kmh: Float, base: Float = 0.30f): Float {
    val s = kmh.coerceAtLeast(0f)
    val b = base.coerceIn(0f, 0.8f)
    return when {
        s <= 20f -> b + (0.45f-b) * s/20f
        s <= 40f -> 0.45f + 0.20f*(s-20f)/20f
        s <= 60f -> 0.65f + 0.20f*(s-40f)/20f
        s <= 80f -> 0.85f + 0.15f*(s-60f)/20f
        else -> 1f
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var speed by remember { mutableStateOf(0f) }
    var auto by remember { mutableStateOf(true) }
    var base by remember { mutableStateOf(0.30f) }
    var title by remember { mutableStateOf("آهنگی انتخاب نشده") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            controller?.setMediaItem(MediaItem.fromUri(it))
            controller?.prepare()
            controller?.play()
            title = it.lastPathSegment ?: "آهنگ"
        }
    }

    DisposableEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_MEDIA_AUDIO
            ))
        }

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future: ListenableFuture<MediaController> = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = future.get() }, context.mainExecutor)

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { speed = (it.speed * 3.6f).coerceAtLeast(0f) }
            }
        }
        try { client.requestLocationUpdates(request, callback, context.mainLooper) }
        catch (_: SecurityException) {}

        onDispose {
            client.removeLocationUpdates(callback)
            controller?.release()
        }
    }

    LaunchedEffect(speed, auto, base, controller) {
        if (auto && controller != null) {
            val target = volumeForSpeed(speed, base)
            while (kotlin.math.abs(controller!!.volume - target) > 0.01f) {
                val next = controller!!.volume + (target - controller!!.volume) * 0.15f
                controller!!.volume = next
                delay(120)
            }
            controller!!.volume = target
        }
    }

    MaterialTheme {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Speed Volume Player", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(22.dp))
            Text("${speed.toInt()} km/h", style = MaterialTheme.typography.displayMedium)
            Text("صدای هدف: ${(volumeForSpeed(speed, base)*100).toInt()}٪")
            Spacer(Modifier.height(18.dp))
            Text(title, maxLines = 1)
            Spacer(Modifier.height(18.dp))

            Row {
                Button(onClick = { picker.launch("audio/*") }) { Text("انتخاب آهنگ") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    controller?.let { if (it.isPlaying) it.pause() else it.play() }
                }) { Text("پخش / توقف") }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تنظیم صدا با سرعت")
                Spacer(Modifier.width(10.dp))
                Switch(checked = auto, onCheckedChange = { auto = it })
            }

            Spacer(Modifier.height(12.dp))
            Text("صدای پایه در توقف: ${(base*100).toInt()}٪")
            Slider(value = base, onValueChange = { base = it }, valueRange = 0.10f..0.60f)

            Spacer(Modifier.height(18.dp))
            Text("۰ km/h = صدای پایه   •   ۸۰ km/h = ۱۰۰٪")
            Text("پخش پس‌زمینه و کنترل صفحه قفل فعال است.")
        }
    }
}
