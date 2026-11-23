package com.nordinary.cyclus.ui.today

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.R
import com.nordinary.cyclus.core.Catalog
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.EpisodeKind
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.engine.CycleStatus
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.insights.Forecast
import com.nordinary.cyclus.insights.ForecastKind
import com.nordinary.cyclus.presenter.OvulationLine
import com.nordinary.cyclus.presenter.TodayHero
import com.nordinary.cyclus.presenter.TodayUiState
import com.nordinary.cyclus.ui.common.CappedFontScale
import com.nordinary.cyclus.ui.common.Badge
import com.nordinary.cyclus.ui.common.CyclusCard
import com.nordinary.cyclus.ui.common.CyclusChip
import com.nordinary.cyclus.ui.common.Droplet
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.Nunito

fun episodeKey(kind: EpisodeKind?) = when (kind) {
    EpisodeKind.HORMONAL_CONTRACEPTION -> "ep_hbc"; EpisodeKind.PREGNANCY -> "ep_preg"; EpisodeKind.POSTPARTUM -> "ep_pp"
    else -> "ep_hbc"
}

/** Content of the Today tab (prototype `renderToday`, track mode). */
@Composable
fun TodayContent(
    state: TodayUiState,
    fmt: Fmt,
    contentPadding: PaddingValues,
    debugNote: String?,
    onOpenLog: () -> Unit,
    onPeriodStarted: () -> Unit,
    onQuickFlow: (Flow) -> Unit,
    onPatterns: () -> Unit,
    onChangeStage: () -> Unit,
    onLh: (LhResult) -> Unit = {},
    onWhy: () -> Unit = {},
    onMethod: () -> Unit = {},
    /** In-context opt-in for „Periode bald“ (nothing is armed before the user says yes, on every API level). */
    reminderOffer: Boolean = false,
    onReminderOffer: (Boolean) -> Unit = {},
    /** One-tap pinned symptoms on the „Heute eingetragen“ card (null: not shown). */
    onToggleSymptom: ((String) -> Unit)? = null,
) {
    val c = Cyclus.colors
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        val hero = state.hero
        var ringHelp by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        CycleRing(
            state.ring, ringA11y(state, fmt),
            Modifier.fillMaxWidth(.82f).align(Alignment.CenterHorizontally).padding(top = 4.dp),
            onExplain = { ringHelp = true }, explainLabel = fmt.s("ringHelpA"),
        ) { RingCenter(hero, fmt) }
        if (ringHelp) com.nordinary.cyclus.ui.sheets.RingHelpSheet(state.ring, fmt, onMethod = { ringHelp = false; onMethod() }) { ringHelp = false }

        // under the ring
        Column(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (hero) {
                TodayHero.Empty -> {
                    PrimaryButton(fmt.s("periodStarted"), onPeriodStarted)
                    SecondaryButton(fmt.s("logEarlier"), onOpenLog)
                }
                is TodayHero.Paused -> {
                    Badge(fmt.s("stillLogged"))
                    LinkRow(fmt.s("changeStage"), onChangeStage, fill = false)
                }
                is TodayHero.InPeriod -> QuickFlowRow(hero.todaysFlow, fmt, onQuickFlow)
                is TodayHero.Track -> {
                    Badge(fmt.s("tier_${hero.tier.key}"), dot = if (hero.rough) c.warning else c.success)
                    if (hero.showPeriodStartButton) PrimaryButton(fmt.s("periodStarted"), onPeriodStarted)
                }
                is TodayHero.ConceiveFertile -> {
                    val ov = when (val o = hero.ovulation) {
                        OvulationLine.TodayOrTomorrow -> fmt.s("ovSoonTT")
                        is OvulationLine.Soon -> fmt.s("ovSoon", fmt.range(o.from, o.to))
                        is OvulationLine.Estimated -> fmt.s("ovEst", fmt.range(o.from, o.to))
                    }
                    Text(ov, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink, textAlign = TextAlign.Center)
                    Badge(fmt.s(hero.chip.key), dot = c.fertile, color = c.fertile)
                }
                is TodayHero.ConceiveAfter -> {
                    Text(fmt.s("ovOccurred", fmt.md(hero.ovulationDay)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink, textAlign = TextAlign.Center)
                    Badge(fmt.s(hero.chip.key), dot = c.fertile, color = c.fertile)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (reminderOffer) CyclusCard {
                CardTitle(androidx.compose.ui.res.stringResource(R.string.offer_t))
                CardBody(androidx.compose.ui.res.stringResource(R.string.offer_b))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(androidx.compose.ui.res.stringResource(R.string.offer_no), { onReminderOffer(false) }, Modifier.weight(1f))
                    PrimaryButton(androidx.compose.ui.res.stringResource(R.string.offer_yes), { onReminderOffer(true) }, Modifier.weight(1f))
                }
            }
            CyclusCard(onClick = onOpenLog) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CardTitle(fmt.s("todaysLog"))
                    Text("›", color = c.ink2, fontSize = 18.sp)
                }
                TodayLogSummary(state.todaysLog, fmt)
                // the most common entries in one tap, without opening the sheet
                if (onToggleSymptom != null) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (s in Catalog.PINNED) CyclusChip(fmt.item(s), state.todaysLog?.symptoms?.contains(s) == true, { onToggleSymptom(s) })
                    }
                }
            }
            val conceive = state.conceive
            if (conceive != null) {
                conceive.earliestTest?.let { test ->
                    CyclusCard { CardTitle(fmt.s("testTitle")); Text(fmt.rich("testBody", fmt.wmd(test)), fontSize = 14.sp, color = c.ink2) }
                }
                if (conceive.bestDays.isNotEmpty()) CyclusCard {
                    CardTitle(fmt.s("bestDays"))
                    Text(
                        conceive.bestDays.joinToString(" · ") { d -> when (d) { state.today -> fmt.s("today"); state.today + 1 -> fmt.s("tomorrow"); else -> fmt.wmd(d) } },
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink,
                    )
                    CardBody(fmt.s("bestTip"))
                }
                conceive.lhPlanStart?.let { start ->
                    CyclusCard { CardTitle(fmt.s("lhTests")); Text(fmt.rich("lhPlan", fmt.wmd(start), conceive.cyclesUsed), fontSize = 14.sp, color = c.ink2) }
                }
                if (conceive.lhToday) CyclusCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        CardTitle(fmt.s("lhToday"))
                        Text(fmt.s("lhNegSoFar", conceive.lhNegativesSoFar), fontSize = 12.sp, color = c.ink2)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in LhResult.entries) CyclusChip(
                            fmt.s("lh_${r.key}"), conceive.todaysLh == r, { onLh(r) }, accent = c.fertile,
                        )
                    }
                }
                state.forecast?.let { ForecastCard(it, fmt) }
                LinkRow(fmt.s("whyLink"), onWhy)
            } else {
                state.forecast?.let { ForecastCard(it, fmt) }
                state.trackFertileWindow?.let { (a, b) ->
                    CyclusCard {
                        CardTitle(fmt.s("fertWinTrack"))
                        CardBody("${fmt.range(a, b)}. ${fmt.s("notBC")}")
                    }
                }
                state.preWindowFrom?.let { from ->
                    CyclusCard {
                        CardTitle(fmt.s("preTitle", fmt.wmd(from)))
                        CardBody(fmt.s("preBody"))
                    }
                }
            }
            if (state.patternCount > 0) LinkRow(fmt.plural("nPatterns", state.patternCount), onPatterns)
        }
        if (state.fertilityFooter) {
            Text(fmt.s("notBC"), Modifier.fillMaxWidth().padding(top = 6.dp), fontSize = 12.sp, color = c.ink2, textAlign = TextAlign.Center)
        }
        if (debugNote != null) {
            Text(debugNote, Modifier.fillMaxWidth().padding(top = 12.dp), fontSize = 11.sp, color = c.ink2, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun RingCenter(hero: TodayHero, fmt: Fmt) {
    val c = Cyclus.colors
    var kicker = ""; var big = ""; var bigMax = 40; var sub: String? = null; var range: String? = null; var subBelow: String? = null
    var bigColor = c.ink
    when (hero) {
        is TodayHero.ConceiveFertile -> {
            kicker = fmt.s("fertToday"); big = fmt.s("ft${hero.tier}"); bigMax = 36
            if (hero.tier > 0) bigColor = c.fertile
            val opens = hero.windowOpensIn
            sub = when {
                opens != null -> fmt.plural("windowOpens", opens)
                hero.windowPassed -> fmt.s("windowPassed")
                else -> fmt.s("cycleDayN", hero.cycleDay)
            }
        }
        is TodayHero.ConceiveAfter -> { kicker = fmt.s("afterOv"); big = fmt.s("dayN", hero.daysAfter); sub = fmt.s("cycleDayN", hero.cycleDay) }
        TodayHero.Empty -> { kicker = fmt.s("welcome"); big = fmt.s("letsStart"); bigMax = 30; sub = fmt.s("startSub") }
        is TodayHero.Paused -> {
            kicker = fmt.s("cycleDayN", hero.cycleDay); big = fmt.s("paused"); bigMax = 32
            sub = fmt.s("pausedSub", fmt.s(episodeKey(hero.episode)))
        }
        is TodayHero.InPeriod -> { kicker = fmt.s("period"); big = fmt.s("dayN", hero.periodDay); sub = fmt.s("howHeavy") }
        is TodayHero.Track -> {
            kicker = fmt.s("cycleDay"); big = hero.cycleDay.toString(); bigMax = 46
            when (hero.status) {
                // most likely date first, the range (how sure that is) below it
                CycleStatus.BETWEEN -> { sub = fmt.s("nextPeriod"); range = fmt.s("around", fmt.md(hero.likely)); subBelow = fmt.s("possibleR", fmt.range(hero.lo, hero.hi)) }
                CycleStatus.PRE -> { sub = fmt.s("periodIn"); range = fmt.s("aboutDays", hero.daysToLikely); subBelow = fmt.s("possibleR", fmt.range(hero.lo, hero.hi)) }
                CycleStatus.EXPECTED -> { sub = fmt.s("expectedNow"); range = fmt.range(hero.lo, hero.hi) }
                CycleStatus.LATER -> { sub = fmt.s("later"); subBelow = fmt.s("expectedOn", fmt.range(hero.lo, hero.hi)) }
                else -> {}
            }
        }
    }
    Text(kicker, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(
        big, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, color = bigColor, maxLines = 1, softWrap = false,
        lineHeight = (bigMax * 1.05).sp, letterSpacing = (-0.4).sp, textAlign = TextAlign.Center,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = bigMax.sp, stepSize = 1.sp),
    )
    sub?.let { Text(it, fontSize = 13.sp, color = c.ink2, lineHeight = 17.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis) }
    range?.let { Text(it, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.ink, maxLines = 1, softWrap = false) }
    subBelow?.let { Text(it, fontSize = 13.sp, color = c.ink2, maxLines = 2, textAlign = TextAlign.Center) }
}

private fun ringA11y(state: TodayUiState, fmt: Fmt): String {
    val a = state.ringA11y
    val cd = a.cycleDay; val lo = a.lo; val hi = a.hi
    if (cd == null || lo == null || hi == null) return fmt.s("ringNone")
    if (state.hero is TodayHero.Paused) return "${fmt.s("cycleDayN", cd)}. ${fmt.s("pausedSub", fmt.s(episodeKey(a.paused)))}."
    var s = fmt.s("ringA", cd, fmt.md(lo), fmt.md(hi))
    a.fertile?.let { (x, y) -> s += fmt.s("ringF", fmt.md(x), fmt.md(y)) }
    return s
}

@Composable
private fun QuickFlowRow(current: Flow?, fmt: Fmt, onQuickFlow: (Flow) -> Unit) {
    val c = Cyclus.colors
    val options = listOf(Flow.LIGHT, Flow.MEDIUM, Flow.HEAVY, Flow.SPOTTING, Flow.NONE)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (f in options) {
            val label = if (f == Flow.NONE) fmt.s("ended") else fmt.item(f.key)
            DropTile(f, label, selected = f != Flow.NONE && current == f, color = if (f == Flow.NONE) c.ink2 else c.period, Modifier.weight(1f)) { onQuickFlow(f) }
        }
    }
}

/** Flow tile (prototype `.drop`). */
@Composable
fun DropTile(flow: Flow, label: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Cyclus.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .heightIn(min = 74.dp)
            .clip(shape)
            .background(if (selected) c.period.copy(alpha = .12f).compositeOver(c.surface) else c.surface)
            .border(1.dp, if (selected) c.period else c.line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Droplet(flow, color)
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 13.sp, textAlign = TextAlign.Center, color = c.ink, style = TextStyle(hyphens = Hyphens.Auto))
    }
}

@Composable
private fun TodayLogSummary(l: DayLog?, fmt: Fmt) {
    val c = Cyclus.colors
    val empty = l == null || (l.flow == null && l.symptoms.isEmpty() && l.emotions.isEmpty() && l.mood == null && l.lh == null && l.mucus == null && l.bbt == null)
    if (empty) { CardBody(fmt.s("nothingYet")); return }
    l!!
    val text = buildAnnotatedString {
        val bits = mutableListOf<() -> Unit>()
        if (l.flow != null && l.flow != Flow.NONE) bits += {
            withStyle(SpanStyle(color = c.period, fontWeight = FontWeight.Bold)) {
                append(fmt.s("flowWord", fmt.item(l.flow!!.key).replace("­", "").lowercase(fmt.locale)))
            }
        }
        val sym = Catalog.SYMPTOMS.filter { it in l.symptoms } + l.symptoms.filter { it !in Catalog.SYMPTOMS }
        if (sym.isNotEmpty()) bits += { append(sym.take(3).joinToString(" · ") { fmt.item(it) } + if (sym.size > 3) " +${sym.size - 3}" else "") }
        val emo = Catalog.EMOTIONS.filter { it in l.emotions }
        if (emo.isNotEmpty()) bits += { append(emo.joinToString(" · ") { fmt.item(it) }) }
        l.mood?.let { m -> bits += { append(fmt.s("moodN", m)) } }
        l.lh?.let { lh -> bits += { append(fmt.s("lhWord", fmt.s("lh_${lh.key}").lowercase(fmt.locale))) } }
        l.mucus?.let { m -> bits += { append(fmt.item(m.key)) } }
        l.bbt?.let { t -> bits += { append("${fmt.temp(t)} °C") } }
        bits.forEachIndexed { i, b -> if (i > 0) append(" · "); b() }
    }
    Text(text, fontSize = 14.sp, color = c.ink)
}

/** „Ausblick“ 7-day grid (prototype `forecastCard`). */
@Composable
private fun ForecastCard(fc: Forecast, fmt: Fmt) {
    val c = Cyclus.colors
    fun label(k: String, kind: ForecastKind, lo: Boolean) = if (kind == ForecastKind.SCALE) "${fmt.s(k)} ${if (lo) "↓" else "↑"}" else fmt.item(k)
    val summary = fc.rows.take(2).map { r ->
        val what = if (r.kind == ForecastKind.SCALE) fmt.s("fcS_${r.key}_${r.dir!!.key}") else fmt.s("fcItem", fmt.item(r.key))
        val whenTxt = when (r.first) { 0 -> fmt.s("fcToday"); 1 -> fmt.s("fcTomorrow"); else -> fmt.s("fcFrom", fmt.weekday(fc.days[r.first])) }
        "$what $whenTxt" + (r.reason?.let { " (${fmt.s("fcAfter", fmt.item(it))})" } ?: "")
    }
    CyclusCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardTitle(fmt.s("fcT"))
            Text(fmt.s("fc7"), fontSize = 12.sp, color = c.ink2)
        }
        if (summary.isNotEmpty()) Text(summary.joinToString("\n"), fontSize = 14.sp, color = c.ink)
        CappedFontScale { Column(
            Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { contentDescription = "${fmt.s("fcAria")}. ${summary.joinToString(". ")}" },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(92.dp))
                fc.days.forEachIndexed { i, d ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) fmt.s("todayShort") else fmt.weekday(d), fontSize = 10.sp, color = c.ink2, maxLines = 1)
                        Text("${fmt.dayOfMonth(d)}", fontFamily = Nunito, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    }
                }
            }
            if (fc.rows.isEmpty()) Text(fmt.s("fcCalm"), fontSize = 11.sp, color = c.ink2)
            for (r in fc.rows) {
                val col = if (r.kind == ForecastKind.SCALE || Catalog.isEmotion(r.key)) c.pre else c.period
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label(r.key, r.kind, r.dir?.key == "lo"), Modifier.width(92.dp), fontSize = 11.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    for (cell in r.cells) {
                        val a = if (cell > 0) (0.2 + cell * 0.8).toFloat() else 0.07f
                        Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(col.copy(alpha = a)))
                    }
                }
            }
        } }
        Text(fmt.s("fcFoot", fc.cycles), fontSize = 12.sp, color = c.ink2)
    }
}

@Composable
private fun CardTitle(text: String) = Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Cyclus.colors.ink)

@Composable
private fun CardBody(text: String) = Text(text, fontSize = 14.sp, color = Cyclus.colors.ink2)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Cyclus.colors
    Button(
        onClick, modifier.heightIn(min = 50.dp), enabled = enabled, shape = RoundedCornerShape(25.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cyclus.colors
    Button(
        onClick, modifier.heightIn(min = 38.dp), shape = RoundedCornerShape(19.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.surface2, contentColor = c.ink),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
fun LinkRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, fill: Boolean = true) {
    val c = Cyclus.colors
    Row(
        modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 2.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, color = c.accent, fontSize = 15.sp)
        Spacer(Modifier.size(8.dp))
        Text("›", fontWeight = FontWeight.SemiBold, color = c.accent, fontSize = 18.sp)
    }
}
