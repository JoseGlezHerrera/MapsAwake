package dev.mapsawake

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log

const val TAG = "MapsAwake"

/** Paquete de Google Maps en el reloj (comprobado con dumpsys en el Watch7). */
const val MAPS_PACKAGE = "com.google.android.apps.maps"

/** Máximo que acepta screen_off_timeout (Integer.MAX_VALUE ≈ 24,8 días). */
const val MAX_TIMEOUT = Int.MAX_VALUE

/**
 * Guarda los valores originales y los vuelve a poner.
 *
 * Usa almacenamiento "device protected" para poder restaurar también en el
 * arranque, antes de que el usuario desbloquee el reloj.
 */
object State {
    const val K_ENABLED = "enabled"
    const val K_BRIGHTNESS = "max_brightness"
    private const val K_APPLIED = "applied"
    private const val K_APPLIED_BOOT = "applied_boot"
    private const val K_ORIG_TIMEOUT = "orig_timeout"
    private const val K_ORIG_BRIGHTNESS = "orig_brightness"
    private const val K_ORIG_BRIGHTNESS_MODE = "orig_brightness_mode"
    private const val K_BRIGHTNESS_APPLIED = "brightness_applied"
    const val K_STATUS = "status"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.createDeviceProtectedStorageContext()
            .getSharedPreferences("mapsawake", Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = prefs(ctx).getBoolean(K_ENABLED, true)
    fun setEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(K_ENABLED, v).apply()

    fun wantsMaxBrightness(ctx: Context) = prefs(ctx).getBoolean(K_BRIGHTNESS, false)
    fun setMaxBrightness(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(K_BRIGHTNESS, v).apply()

    fun isApplied(ctx: Context) = prefs(ctx).getBoolean(K_APPLIED, false)
    fun appliedBoot(ctx: Context) = prefs(ctx).getInt(K_APPLIED_BOOT, -1)
    fun origTimeout(ctx: Context) = prefs(ctx).getInt(K_ORIG_TIMEOUT, -1)

    fun setStatus(ctx: Context, s: String) = prefs(ctx).edit().putString(K_STATUS, s).apply()
    fun status(ctx: Context) = prefs(ctx).getString(K_STATUS, "Esperando a Maps") ?: ""

    fun canWrite(ctx: Context) = Settings.System.canWrite(ctx)

    fun bootCount(ctx: Context): Int =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /**
     * Maps acaba de entrar: guarda los valores actuales (solo si no hay ya una
     * copia pendiente de restaurar, para no guardar nuestros propios valores)
     * y pone el timeout al máximo y, si se pidió, el brillo manual al máximo.
     */
    fun apply(ctx: Context): Boolean {
        if (!canWrite(ctx)) {
            Log.w(TAG, "Sin WRITE_SETTINGS: no puedo cambiar el timeout")
            return false
        }
        val cr = ctx.contentResolver
        val p = prefs(ctx)
        if (!p.getBoolean(K_APPLIED, false)) {
            p.edit()
                .putInt(K_ORIG_TIMEOUT, Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 15000))
                .putInt(K_ORIG_BRIGHTNESS, Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, -1))
                .putInt(K_ORIG_BRIGHTNESS_MODE, Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, -1))
                .putBoolean(K_APPLIED, true)
                .putInt(K_APPLIED_BOOT, bootCount(ctx))
                .commit() // síncrono: la copia debe existir antes de tocar nada
        }
        Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, MAX_TIMEOUT)
        if (wantsMaxBrightness(ctx)) {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 255)
            p.edit().putBoolean(K_BRIGHTNESS_APPLIED, true).commit()
        } else {
            restoreBrightness(ctx) // por si se desactivó la opción con Maps delante
        }
        Log.i(TAG, "Aplicado: timeout=$MAX_TIMEOUT brillo=${wantsMaxBrightness(ctx)}")
        return true
    }

    /** Vuelve a dejar los valores guardados. No hace nada si no hay copia. */
    fun restore(ctx: Context) {
        val p = prefs(ctx)
        if (!p.getBoolean(K_APPLIED, false)) return
        if (!canWrite(ctx)) {
            Log.w(TAG, "Sin WRITE_SETTINGS: no puedo restaurar")
            return
        }
        val t = p.getInt(K_ORIG_TIMEOUT, -1)
        if (t > 0) Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, t)
        restoreBrightness(ctx)
        p.edit().putBoolean(K_APPLIED, false).commit()
        Log.i(TAG, "Restaurado: timeout=$t")
    }

    /** Solo si fuimos nosotros quienes lo cambiamos. */
    private fun restoreBrightness(ctx: Context) {
        val cr = ctx.contentResolver
        val p = prefs(ctx)
        if (!p.getBoolean(K_BRIGHTNESS_APPLIED, false)) return
        val b = p.getInt(K_ORIG_BRIGHTNESS, -1)
        val m = p.getInt(K_ORIG_BRIGHTNESS_MODE, -1)
        // Primero el valor y luego el modo, para que el automático no salte
        // con el brillo al 255.
        if (b >= 0) Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, b)
        if (m >= 0) Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, m)
        p.edit().putBoolean(K_BRIGHTNESS_APPLIED, false).commit()
    }
}
