package dev.mapsawake

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/** Tile: muestra el estado y activa/desactiva MapsAwake al tocarla. */
class MapsAwakeTile : TileService() {

    override fun onTileRequest(req: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        if (req.currentState.lastClickableId == CLICK_TOGGLE) {
            val on = !State.isEnabled(this)
            State.setEnabled(this, on) // el servicio reacciona al cambio
            if (!on) State.restore(this) // por si el servicio no está corriendo
        }
        val on = State.isEnabled(this)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(on)))
            .build()
        return done(tile)
    }

    override fun onTileResourcesRequest(
        req: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        done(ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build())

    private fun layout(on: Boolean): LayoutElementBuilders.LayoutElement {
        val button = LayoutElementBuilders.Box.Builder()
            .setWidth(wrap()).setHeight(wrap())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(CLICK_TOGGLE)
                            .setOnClick(ActionBuilders.LoadAction.Builder().build())
                            .build()
                    )
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(if (on) 0xFF1A73E8.toInt() else 0xFF5F6368.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(26f)).build())
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(22f)).setEnd(dp(22f)).setTop(dp(12f)).setBottom(dp(12f))
                            .build()
                    )
                    .build()
            )
            .addContent(text(if (on) "ACTIVO" else "APAGADO", 16f, 0xFFFFFFFF.toInt(), bold = true))
            .build()

        return LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("MapsAwake", 15f, 0xFFFFFFFF.toInt(), bold = true))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(10f)).build())
            .addContent(button)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(10f)).build())
            .addContent(text(if (on) State.status(this) else "Toca para activar", 12f, 0xFFBDC1C6.toInt()))
            .build()
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) =
        LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setMaxLines(2)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD
                               else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build()
            )
            .build()

    companion object {
        private const val CLICK_TOGGLE = "toggle"
        private const val RES_VERSION = "1"

        private fun <T : Any> done(v: T): ListenableFuture<T> =
            CallbackToFutureAdapter.getFuture { it.set(v) }

        /** Pide al sistema que vuelva a pintar la tile. */
        fun refresh(ctx: Context) {
            try { getUpdater(ctx).requestUpdate(MapsAwakeTile::class.java) } catch (_: Exception) {}
        }
    }
}
