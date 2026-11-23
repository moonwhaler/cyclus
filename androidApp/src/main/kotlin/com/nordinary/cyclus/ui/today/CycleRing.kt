package com.nordinary.cyclus.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.nordinary.cyclus.ui.common.CyclusIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.nordinary.cyclus.presenter.RingModel
import com.nordinary.cyclus.presenter.SegmentKind
import com.nordinary.cyclus.ui.theme.Cyclus
import kotlin.math.cos
import kotlin.math.sin

/** The cycle ring (prototype `ringSVG`, 300-unit viewBox), with [center] text inside. One semantics node. */
@Composable
fun CycleRing(
    model: RingModel, a11y: String, modifier: Modifier = Modifier,
    /** Opens the explainer: the whole ring is a tap target, the ⓘ in its bottom gap makes that discoverable. */
    onExplain: (() -> Unit)? = null, explainLabel: String = "",
    center: @Composable () -> Unit,
) {
    val c = Cyclus.colors
    BoxWithConstraints(modifier.widthIn(max = 300.dp).aspectRatio(1f)) {
        Canvas(
            Modifier.fillMaxSize()
                .then(if (onExplain != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = explainLabel, role = Role.Button, onClick = onExplain) else Modifier)
                .clearAndSetSemantics {
                    contentDescription = a11y
                    if (onExplain != null) { role = Role.Button; onClick(explainLabel) { onExplain(); true } }
                },
        ) {
            val u = size.minDimension / 300f
            val cx = size.width / 2; val cy = size.height / 2
            fun arc(r: Float, a0: Float, a1: Float, color: androidx.compose.ui.graphics.Color, w: Float, dash: FloatArray? = null, cap: StrokeCap = StrokeCap.Butt) =
                drawArc(
                    color = color, startAngle = a0, sweepAngle = a1 - a0, useCenter = false,
                    topLeft = Offset(cx - r * u, cy - r * u), size = Size(2 * r * u, 2 * r * u),
                    style = Stroke(width = w * u, cap = cap, pathEffect = dash?.let { d -> PathEffect.dashPathEffect(FloatArray(d.size) { d[it] * u }) }),
                )
            fun pol(r: Float, deg: Float) = Offset(cx + r * u * cos(Math.toRadians(deg.toDouble())).toFloat(), cy + r * u * sin(Math.toRadians(deg.toDouble())).toFloat())

            if (model.segments.isEmpty()) {
                arc(118f, 135f, 405f, c.mid.copy(alpha = .3f), 18f, cap = StrokeCap.Round)
                return@Canvas
            }
            for (s in model.segments) {
                val col = when (s.kind) {
                    SegmentKind.BLEED -> c.period; SegmentKind.PRE -> c.pre; SegmentKind.FERTILE -> c.fertile; SegmentKind.NEUTRAL -> c.mid
                }
                arc(118f, s.startDeg, s.endDeg, col.copy(alpha = s.alpha), 18f, if (s.dashed) floatArrayOf(2f, 3.2f) else null)
            }
            model.predicted?.let { (a0, a1) -> arc(137f, a0, a1, c.period, 3f, floatArrayOf(5f, 4f), StrokeCap.Round) }
            model.ovulationDeg?.let { a ->
                val o = pol(137f, a)
                drawCircle(c.bg, 7f * u, o)
                drawCircle(c.fertile, 7f * u, o, style = Stroke(2f * u, pathEffect = if (model.ovulationSolid) null else PathEffect.dashPathEffect(floatArrayOf(2.5f * u, 2f * u))))
                drawCircle(c.fertile, 2.6f * u, o)
            }
            model.knobDeg?.let { a ->
                val k = pol(118f, a)
                drawCircle(c.bg, 15f * u, k)
                drawCircle(c.ink, 11f * u, k)
            }
        }
        // prototype .ringcenter: left/right 21%, top 22%, bottom 24%
        Box(
            Modifier.fillMaxSize().padding(start = maxWidth * .21f, end = maxWidth * .21f, top = maxHeight * .22f, bottom = maxHeight * .24f),
            contentAlignment = Alignment.Center,
        ) { Column(horizontalAlignment = Alignment.CenterHorizontally) { center() } }
        // the gap at the bottom of the ring (45°–135°) is empty: a quiet ⓘ sits there
        if (onExplain != null && model.segments.isNotEmpty()) IconButton(
            onClick = onExplain,
            modifier = Modifier.align(Alignment.BottomCenter).size(44.dp).semantics { contentDescription = explainLabel },
        ) { Icon(CyclusIcons.Info, null, Modifier.size(18.dp), tint = c.ink2.copy(alpha = .75f)) }
    }
}
