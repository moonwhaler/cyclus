package com.nordinary.cyclus.widget

import com.nordinary.cyclus.ui.theme.AppearanceStore
import com.nordinary.cyclus.presenter.Themes
import com.nordinary.cyclus.presenter.ThemeTokens
import com.nordinary.cyclus.presenter.ThemeChoice
import com.nordinary.cyclus.presenter.Palette
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.nordinary.cyclus.ActionEntry
import com.nordinary.cyclus.R
import com.nordinary.cyclus.notify.AlarmArmer
import com.nordinary.cyclus.notify.NotifyFiles
import com.nordinary.cyclus.presenter.WidgetLine
import com.nordinary.cyclus.presenter.WidgetPresenter
import com.nordinary.cyclus.presenter.WidgetView
import java.time.LocalDate

/**
 * Home-screen widgets (spec §8.1, ANDROID_PLAN §6.2). Read only the widget side file (never the database) and
 * never write: taps open the app through the non-exported [ActionEntry] (single writer, spec §3.3).
 */
object CyclusWidgets {
    private val receivers = listOf(SmallWidgetReceiver::class.java, MediumWidgetReceiver::class.java)

    fun anyPlaced(ctx: Context): Boolean {
        val am = AppWidgetManager.getInstance(ctx) ?: return false
        return receivers.any { am.getAppWidgetIds(ComponentName(ctx, it)).isNotEmpty() }
    }

    /** Bumps a version in each widget's Glance state so running sessions re-read the side file, then updates. */
    suspend fun refresh(ctx: Context) {
        val mgr = GlanceAppWidgetManager(ctx)
        for (w in listOf(SmallWidget(), MediumWidget())) runCatching {
            for (id in mgr.getGlanceIds(w.javaClass)) {
                androidx.glance.appwidget.state.updateAppWidgetState(ctx, id) { it[VERSION] = System.currentTimeMillis() }
                w.update(ctx, id)
            }
        }
    }

    internal val VERSION = androidx.datastore.preferences.core.longPreferencesKey("v")
}

/**
 * Widget colours: neutrals and the accent of the chosen theme (Settings › Erscheinungsbild), light/dark by the
 * launcher. Material You falls back to Rose (Glance has no app-level dynamic scheme here). The ring keeps its fixed
 * cycle colours.
 */
private object WColors {
    private fun pair(f: (ThemeTokens) -> Long): androidx.glance.unit.ColorProvider {
        val (day, night) = when (val ch = AppearanceStore.state.value.choice) {
            is ThemeChoice.Preset -> Themes.tokens(ch.palette, false) to Themes.tokens(ch.palette, true)
            is ThemeChoice.Custom -> Themes.custom(ch.primary, ch.secondary, false) to Themes.custom(ch.primary, ch.secondary, true)
            ThemeChoice.Dynamic -> Themes.tokens(Palette.ROSE, false) to Themes.tokens(Palette.ROSE, true)
        }
        return ColorProvider(day = Color(f(day).toInt()), night = Color(f(night).toInt()))
    }
    val surface get() = pair { it.surface }
    val ink get() = pair { it.ink }
    val ink2 get() = pair { it.ink2 }
    val accent get() = pair { it.accent }
    val onAccent get() = pair { it.onAccent }
    val chip get() = pair { it.accentContainer }
    val onChip get() = pair { it.onAccentContainer }
}

private fun actionIntent(ctx: Context, action: String) = ActionEntry.intent(ctx, action)

/** Mini ring as a bitmap (Glance can't draw arcs); mid-tone colours that read on light and dark. */
private fun ring(progress: Float, px: Int): Bitmap {
    val b = androidx.core.graphics.createBitmap(px, px)
    val c = Canvas(b)
    val stroke = px * 0.11f
    val r = RectF(stroke, stroke, px - stroke, px - stroke)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    p.color = 0x338A7F76; c.drawArc(r, 0f, 360f, false, p)
    p.color = 0xFFC2416A.toInt(); c.drawArc(r, -90f, 360f * progress.coerceAtLeast(0.02f), false, p)
    return b
}

private fun lineText(ctx: Context, l: WidgetLine): String = when (l) {
    is WidgetLine.InPeriod -> ctx.getString(R.string.w_in_period, l.periodDay)
    is WidgetLine.In -> if (l.from == l.to) ctx.resources.getQuantityString(R.plurals.w_in_days, l.from, l.from)
        else ctx.getString(R.string.w_in_range, l.from, l.to)
    WidgetLine.Window -> ctx.getString(R.string.w_window)
    WidgetLine.Later -> ctx.getString(R.string.w_later)
    WidgetLine.Paused -> ctx.getString(R.string.w_paused)
}

abstract class CyclusWidget(private val medium: Boolean) : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // re-read whenever the app or the midnight alarm bumps the version (the session may outlive updates)
            val version = androidx.glance.currentState(CyclusWidgets.VERSION)
            val view = androidx.compose.runtime.remember(version) {
                NotifyFiles.readWidget(context)?.let { WidgetPresenter.view(it, LocalDate.now().toEpochDay().toInt()) }
            }
            val ringBmp = androidx.compose.runtime.remember(view) { view?.let { ring(it.progress, (64 * context.resources.displayMetrics.density).toInt()) } }
            Content(view, ringBmp)
        }
    }

    @Composable
    private fun Content(view: WidgetView?, ringBmp: Bitmap?) {
        val ctx = LocalContext.current
        val base = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp).background(WColors.surface).padding(14.dp)
        if (!medium) {
            Column(
                base.clickable(actionStartActivity(actionIntent(ctx, ActionEntry.LOG))),
                horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
            ) { Summary(ctx, view, ringBmp, compact = true) }
        } else {
            Row(base, verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(actionIntent(ctx, ActionEntry.LOG))),
                    horizontalAlignment = Alignment.CenterHorizontally) { Summary(ctx, view, ringBmp, compact = false) }
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Pill(ctx.getString(R.string.w_btn_period), actionIntent(ctx, ActionEntry.PERIOD), strong = true)
                    Spacer(GlanceModifier.height(8.dp))
                    Pill(ctx.getString(R.string.w_btn_log), actionIntent(ctx, ActionEntry.LOG), strong = false)
                }
            }
        }
    }

    @Composable
    private fun Summary(ctx: Context, view: WidgetView?, ringBmp: Bitmap?, compact: Boolean) {
        if (view == null || ringBmp == null) {
            // neutral state: „Details im Widget“ off, or nothing to predict yet
            Image(ImageProvider(R.drawable.ic_notify), ctx.getString(R.string.app_name), GlanceModifier.size(44.dp), colorFilter = ColorFilter.tint(WColors.accent))
            Spacer(GlanceModifier.height(6.dp))
            Text(ctx.getString(R.string.app_name), style = TextStyle(color = WColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            if (compact) Text(ctx.getString(R.string.w_btn_log), style = TextStyle(color = WColors.ink2, fontSize = 12.sp))
            return
        }
        Box(GlanceModifier.size(64.dp), contentAlignment = Alignment.Center) {
            Image(ImageProvider(ringBmp), null, GlanceModifier.size(64.dp))
            Text(view.cycleDay.toString(), style = TextStyle(color = WColors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(ctx.getString(R.string.w_day, view.cycleDay), style = TextStyle(color = WColors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        Text(lineText(ctx, view.line), style = TextStyle(color = WColors.ink2, fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 2)
    }

    @Composable
    private fun Pill(label: String, intent: Intent, strong: Boolean) {
        Box(
            GlanceModifier.fillMaxWidth().height(44.dp).cornerRadius(22.dp).background(if (strong) WColors.accent else WColors.chip)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = TextStyle(color = if (strong) WColors.onAccent else WColors.onChip, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

class SmallWidget : CyclusWidget(medium = false)
class MediumWidget : CyclusWidget(medium = true)

/** Placing the first widget arms the midnight refresh (DB-free). */
abstract class CyclusWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) { super.onEnabled(context); AlarmArmer.arm(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); AlarmArmer.arm(context) }
}

class SmallWidgetReceiver : CyclusWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SmallWidget() }
class MediumWidgetReceiver : CyclusWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MediumWidget() }
