package dev.mapsawake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Red de seguridad: si el reloj se apagó (o la app se actualizó) con Maps
 * delante, los valores cambiados se quedarían puestos. Aquí se restauran.
 *
 * Solo restaura si los cambios son de un arranque anterior: si el servicio ya
 * ha detectado Maps en ESTE arranque, no lo pisa.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val stale = State.isApplied(ctx) &&
            (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                State.appliedBoot(ctx) != State.bootCount(ctx))
        Log.i(TAG, "${intent.action}: aplicado=${State.isApplied(ctx)} restaurar=$stale")
        if (stale) State.restore(ctx)
    }
}
