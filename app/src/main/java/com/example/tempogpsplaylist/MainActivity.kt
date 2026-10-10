package com.example.tempogpsplaylist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.location.*
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var gpsLabel: TextView
    private lateinit var playbackLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var playlistLabel: TextView
    private lateinit var gpsMinInput: EditText
    private lateinit var gpsMaxInput: EditText
    private lateinit var playMinInput: EditText
    private lateinit var playMaxInput: EditText
    private lateinit var callback: LocationCallback
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var gpsMin = 0f
    private var gpsMax = 20f
    private var playMin = 0.70f
    private var playMax = 1.50f
    private var auto = true
    private var speedKmh = 0f
    private val names = mutableListOf<String>()

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val uris = mutableListOf<android.net.Uri>()
        data.clipData?.let { c -> for (i in 0 until c.itemCount) uris.add(c.getItemAt(i).uri) }
            ?: data.data?.let { uris.add(it) }
        if (uris.isEmpty()) return@registerForActivityResult
        player.clearMediaItems(); names.clear()
        uris.forEachIndexed { i, uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val name = try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            } catch (_: Exception) { null } ?: "Track ${i + 1}"
            names.add(name); player.addMediaItem(MediaItem.fromUri(uri))
        }
        playlistLabel.text = "پلی‌لیست (${uris.size} آهنگ):\n" + names.mapIndexed { i, n -> "${i+1}. $n" }.joinToString("\n")
        player.prepare(); player.play()
        applySpeed(speedKmh)
    }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true || r[Manifest.permission.ACCESS_COARSE_LOCATION] == true) startGps()
        else Toast.makeText(this, "برای کنترل سرعت، مجوز مکان لازم است.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = getSharedPreferences("gps", MODE_PRIVATE)
        gpsMin = p.getFloat("gmin", 0f); gpsMax = p.getFloat("gmax", 20f)
        playMin = p.getFloat("pmin", .70f); playMax = p.getFloat("pmax", 1.50f)
        auto = p.getBoolean("auto", true)
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val i = player.currentMediaItemIndex
                if (i in names.indices) statusLabel.text = "در حال پخش: ${names[i]}"
            }
        })
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location = result.lastLocation ?: return
                if (loc.hasAccuracy() && loc.accuracy > 50f) return
                speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6f).coerceAtLeast(0f) else 0f
                gpsLabel.text = "سرعت GPS: ${fmt(speedKmh)} km/h" +
                    if (loc.hasAccuracy()) " | دقت ${fmt(loc.accuracy)} m" else ""
                applySpeed(speedKmh)
            }
        }
        ui()
        gpsMinInput.setText(fmt(gpsMin)); gpsMaxInput.setText(fmt(gpsMax))
        playMinInput.setText(fmt(playMin)); playMaxInput.setText(fmt(playMax))
    }

    private fun ui() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(18)) }
        scroll.addView(root); setContentView(scroll)
        root.addView(TextView(this).apply { text = "Tempo GPS Playlist"; textSize = 25f; gravity = Gravity.CENTER }, full())
        root.addView(TextView(this).apply { text = "کنترل سرعت پخش بر اساس سرعت حرکت GPS"; gravity = Gravity.CENTER }, full())
        root.addView(btn("انتخاب آهنگ‌ها / پلی‌لیست") {
            picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            })
        }, full())
        statusLabel = label("ابتدا پلی‌لیست انتخاب کن"); root.addView(statusLabel, full())
        gpsLabel = label("سرعت GPS: منتظر موقعیت…"); root.addView(gpsLabel, full())
        playbackLabel = label("سرعت پخش: —").apply { textSize = 20f; gravity = Gravity.CENTER }
        root.addView(playbackLabel, full())
        val row = LinearLayout(this)
        row.addView(btn("▶ پخش") { player.play() }, weight())
        row.addView(btn("Ⅱ مکث") { player.pause() }, weight())
        row.addView(btn("⏮ قبلی") { player.seekToPreviousMediaItem() }, weight())
        row.addView(btn("بعدی ⏭") { player.seekToNextMediaItem() }, weight())
        root.addView(row, full())
        root.addView(label("بازه سرعت GPS (km/h)"), full())
        val a = LinearLayout(this)
        gpsMinInput = input("حداقل GPS"); gpsMaxInput = input("حداکثر GPS")
        a.addView(gpsMinInput, weight()); a.addView(gpsMaxInput, weight()); root.addView(a, full())
        root.addView(label("بازه سرعت پخش (x)"), full())
        val b = LinearLayout(this)
        playMinInput = input("حداقل پخش"); playMaxInput = input("حداکثر پخش")
        b.addView(playMinInput, weight()); b.addView(playMaxInput, weight()); root.addView(b, full())
        root.addView(btn("ذخیره تنظیمات") { save() }, full())
        root.addView(btn("روشن / خاموش کردن کنترل GPS") {
            auto = !auto
            getSharedPreferences("gps", MODE_PRIVATE).edit().putBoolean("auto", auto).apply()
            if (auto) applySpeed(speedKmh) else {
                player.playbackParameters = PlaybackParameters(1f)
                playbackLabel.text = "کنترل GPS خاموش — سرعت پخش 1.00x"
            }
        }, full())
        root.addView(btn("شروع GPS") { checkGpsPermission() }, full())
        root.addView(btn("توقف GPS") { fused.removeLocationUpdates(callback); gpsLabel.text = "GPS متوقف شد" }, full())
        playlistLabel = label("پلی‌لیست هنوز انتخاب نشده است"); root.addView(playlistLabel, full())
    }

    private fun applySpeed(kmh: Float) {
        if (!auto) return
        val ratio = ((kmh - gpsMin) / (gpsMax - gpsMin)).coerceIn(0f, 1f)
        val playback = playMin + ratio * (playMax - playMin)
        player.playbackParameters = PlaybackParameters(playback)
        playbackLabel.text = "سرعت پخش: ${fmt(playback)}x"
    }

    private fun save() {
        val a = gpsMinInput.text.toString().toFloatOrNull()
        val b = gpsMaxInput.text.toString().toFloatOrNull()
        val c = playMinInput.text.toString().toFloatOrNull()
        val d = playMaxInput.text.toString().toFloatOrNull()
        if (a == null || b == null || c == null || d == null || a < 0 || b <= a || b > 300 ||
            c < .25f || d <= c || d > 3f) {
            Toast.makeText(this, "GPS: 0 تا 300 و حداکثر بزرگ‌تر از حداقل؛ پخش: 0.25x تا 3.00x.", Toast.LENGTH_LONG).show()
            return
        }
        gpsMin = a; gpsMax = b; playMin = c; playMax = d
        getSharedPreferences("gps", MODE_PRIVATE).edit().putFloat("gmin", a).putFloat("gmax", b)
            .putFloat("pmin", c).putFloat("pmax", d).apply()
        applySpeed(speedKmh)
        Toast.makeText(this, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
    }

    private fun checkGpsPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) startGps()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    @Suppress("MissingPermission")
    private fun startGps() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).setMinUpdateIntervalMillis(700L).build()
        fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        statusLabel.text = "دریافت سرعت GPS فعال است"
    }

    private fun input(h: String) = EditText(this).apply {
        hint = h; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; setSingleLine(true)
    }
    private fun label(s: String) = TextView(this).apply { text = s; textSize = 15f; setPadding(dp(6), dp(8), dp(6), dp(8)) }
    private fun btn(s: String, f: () -> Unit) = Button(this).apply { text = s; setOnClickListener { f() } }
    private fun full() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(5) }
    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(3) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(v: Float) = String.format(Locale.US, "%.2f", v)

    override fun onDestroy() {
        if (::callback.isInitialized) fused.removeLocationUpdates(callback)
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
