package dev.mapsawake

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Se entera de los cambios de ventana (eventos, sin sondeo) y decide si Maps
 * es la app que está delante.
 *
 * Con Maps delante:
 *  - screen_off_timeout al máximo (y brillo, si se pidió), vía [State.apply];
 *  - una ventana de 1×1 px, transparente y que no recibe toques, con
 *    FLAG_KEEP_SCREEN_ON. Esto hace falta porque One UI Watch aplica por su
 *    cuenta un timeout de ~7 s al despertar con un golpe de muñeca o al bajar
 *    el brazo, ignorando screen_off_timeout. Mientras esa ventana esté
 *    visible, el sistema no apaga la pantalla por timeout.
 *
 * Al salir Maps, al desactivar la app o el servicio: lo quita todo y restaura.
 */
class MapsWatcherService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val handler = Handler(Looper.getMainLooper())
    private var keepOnView: View? = null
    private var mapsInFront = false
    private var active = false      // ¿hemos aplicado los cambios?
    private var prefsDirty = false  // ¿cambió una opción con Maps delante?

    private val evaluate = Runnable { evaluateNow() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Servicio conectado")
        State.prefs(this).registerOnSharedPreferenceChangeListener(this)
        // Si veníamos de un cierre brusco o un reinicio con valores cambiados,
        // evaluateNow los restaura cuando Maps no está delante.
        schedule(0)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Ignoramos nuestra propia ventana y agrupamos ráfagas de eventos.
        if (event.packageName == packageName) return
        schedule(400)
    }

    override fun onInterrupt() {}

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
        if (key == State.K_ENABLED || key == State.K_BRIGHTNESS) {
            prefsDirty = true
            schedule(0)
        }
    }

    private fun schedule(delayMs: Long) {
        handler.removeCallbacks(evaluate)
        handler.postDelayed(evaluate, delayMs)
    }

    /** ¿La ventana de aplicación de más arriba es de Maps? null = no se sabe. */
    private fun isMapsInFront(): Boolean? {
        val ws = try { windows } catch (e: Exception) { return null }
        if (ws.isNullOrEmpty()) return null
        val top = ws.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .maxByOrNull { it.layer } ?: return false
        val pkg = top.root?.packageName ?: return null
        return pkg == MAPS_PACKAGE
    }

    private fun evaluateNow() {
        // Si no sabemos qué hay delante (p. ej. pantalla en reposo y lista de
        // ventanas vacía), mantenemos el estado anterior.
        val front = isMapsInFront() ?: mapsInFront
        mapsInFront = front
        val want = front && State.isEnabled(this)
        // Solo actuamos en las transiciones (o si cambió una opción), no en
        // cada evento de ventana.
        if (want && (!active || prefsDirty)) enter()
        else if (!want && (active || State.isApplied(this))) exit()
        else if (!want) { State.setStatus(this, idleStatus()); notifyUi() }
        prefsDirty = false
    }

    private fun idleStatus() =
        if (State.isEnabled(this)) "Esperando a Maps" else "Desactivado"

    private fun enter() {
        val ok = State.apply(this)
        addKeepOnView()
        active = true
        State.setStatus(this, if (ok) "Maps delante: pantalla encendida"
                              else "Maps delante, pero falta WRITE_SETTINGS")
        notifyUi()
    }

    private fun exit() {
        removeKeepOnView()
        State.restore(this)
        active = false
        State.setStatus(this, idleStatus())
        notifyUi()
    }

    private fun addKeepOnView() {
        if (keepOnView != null) return
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            getSystemService(WindowManager::class.java).addView(v, lp)
            keepOnView = v
            Log.i(TAG, "Ventana KEEP_SCREEN_ON añadida")
        } catch (e: Exception) {
            Log.e(TAG, "No pude añadir la ventana KEEP_SCREEN_ON", e)
        }
    }

    private fun removeKeepOnView() {
        val v = keepOnView ?: return
        try {
            getSystemService(WindowManager::class.java).removeView(v)
        } catch (e: Exception) {
            Log.w(TAG, "removeView", e)
        }
        keepOnView = null
        Log.i(TAG, "Ventana KEEP_SCREEN_ON quitada")
    }

    private fun notifyUi() {
        sendBroadcast(Intent(ACTION_CHANGED).setPackage(packageName))
        MapsAwakeTile.refresh(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Servicio desactivado: restauro")
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        handler.removeCallbacks(evaluate)
        State.prefs(this).unregisterOnSharedPreferenceChangeListener(this)
        removeKeepOnView()
        State.restore(this)
        active = false
        State.setStatus(this, "Servicio de accesibilidad apagado")
    }

    companion object {
        const val ACTION_CHANGED = "dev.mapsawake.CHANGED"
    }
}
