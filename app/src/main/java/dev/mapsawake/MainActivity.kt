package dev.mapsawake

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Switch
import android.widget.TextView

/** Interruptores ON/OFF y brillo, y el estado actual (incluidos permisos). */
class MainActivity : Activity() {

    private lateinit var swEnabled: Switch
    private lateinit var swBrightness: Switch
    private lateinit var tvStatus: TextView

    private val changed = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        swEnabled = findViewById(R.id.sw_enabled)
        swBrightness = findViewById(R.id.sw_brightness)
        tvStatus = findViewById(R.id.tv_status)

        swEnabled.setOnCheckedChangeListener { _, on ->
            if (on == State.isEnabled(this)) return@setOnCheckedChangeListener
            State.setEnabled(this, on)
            // Si el servicio no está corriendo, restauramos desde aquí.
            if (!on && !serviceEnabled()) State.restore(this)
            MapsAwakeTile.refresh(this)
            refresh()
        }
        swBrightness.setOnCheckedChangeListener { _, on ->
            if (on != State.wantsMaxBrightness(this)) State.setMaxBrightness(this, on)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(changed, IntentFilter(MapsWatcherService.ACTION_CHANGED),
            Context.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    override fun onPause() {
        unregisterReceiver(changed)
        super.onPause()
    }

    private fun serviceEnabled(): Boolean {
        val me = ComponentName(this, MapsWatcherService::class.java)
        val list = Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun refresh() {
        swEnabled.isChecked = State.isEnabled(this)
        swBrightness.isChecked = State.wantsMaxBrightness(this)
        val pm = getSystemService(PowerManager::class.java)
        val ok = "✓"; val ko = "✗"
        val timeout = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
        tvStatus.text = buildString {
            appendLine(if (serviceEnabled()) State.status(this@MainActivity)
                       else "Servicio de accesibilidad APAGADO")
            appendLine()
            appendLine("Timeout: ${fmt(timeout)}")
            if (State.isApplied(this@MainActivity))
                appendLine("Original: ${fmt(State.origTimeout(this@MainActivity))}")
            appendLine()
            appendLine("${if (serviceEnabled()) ok else ko} Accesibilidad")
            appendLine("${if (State.canWrite(this@MainActivity)) ok else ko} WRITE_SETTINGS")
            append("${if (pm.isIgnoringBatteryOptimizations(packageName)) ok else ko} Sin optimización de batería")
        }
    }

    private fun fmt(ms: Int): String = when {
        ms < 0 -> "?"
        ms == MAX_TIMEOUT -> "máximo"
        ms < 60_000 -> "${ms / 1000} s"
        else -> "${ms / 60_000} min"
    }
}
