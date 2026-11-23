package com.nordinary.cyclus

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.i18n.rememberFmt
import com.nordinary.cyclus.presenter.TodayCard
import com.nordinary.cyclus.presenter.TodayLayout
import com.nordinary.cyclus.presenter.TodayPresenter
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import com.nordinary.cyclus.ui.theme.CyclusTheme
import com.nordinary.cyclus.ui.today.TodayContent
import com.nordinary.cyclus.ui.today.TodayEditor
import com.nordinary.cyclus.ui.today.cardTitle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Customizable Today (spec §7.2): hide → library → back at the end, TalkBack reorder, reset, the fixed footer. */
@RunWith(AndroidJUnit4::class)
class TodayLayoutUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun hideAddBackMoveAndReset() {
        var fmt: Fmt? = null
        var layout by mutableStateOf(TodayLayout.DEFAULT)
        rule.setContent {
            CyclusTheme {
                fmt = rememberFmt()
                // on Today the editor sits in the screen's scroll column
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    TodayEditor(layout, fmt!!, onLayout = { layout = it }, onEditQuickPicks = {}, onDone = {})
                }
            }
        }
        val f = fmt!!
        val forecast = cardTitle(TodayCard.FORECAST, f)
        rule.onNodeWithContentDescription("${f.s("hideCard")}: $forecast").performScrollTo().performClick()
        rule.runOnIdle { check(TodayCard.FORECAST in layout.hidden) }
        // back from the library: at the end of the shown cards
        rule.onNodeWithContentDescription("${f.s("showCard")}: $forecast").performScrollTo().performClick()
        rule.runOnIdle { check(layout.shown.last() == TodayCard.FORECAST && TodayCard.FORECAST !in layout.hidden) }
        // TalkBack: „Nach oben“ on the card right after LOG moves it to the top
        val second = layout.shown[1]
        val row = rule.onNode(
            androidx.compose.ui.test.hasText(cardTitle(second, f), substring = true) and
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
        ).fetchSemanticsNode()
        val up = row.config[SemanticsActions.CustomActions].first { it.label == f.s("moveUp") }
        rule.runOnUiThread { up.action() }
        rule.runOnIdle { check(layout.shown.first() == second) }
        rule.onNodeWithText(f.s("resetLayout")).performScrollTo().performClick()
        rule.runOnIdle { check(layout == TodayLayout.DEFAULT) }
    }

    @Test fun fertilityFooterStaysWhenEveryCardIsHidden() {
        val today = LocalDate.now().toEpochDay().toInt()
        val d = SampleScenarios.generate(SampleScenario.CONCEIVE, today)
        val snap = CycleSnapshot.compute(CycleData(d.logs, emptyMap(), today), d.mode)
        val state = TodayPresenter.present(snap, null)
        val allHidden = TodayLayout(TodayCard.entries.toList(), TodayCard.entries.toSet())
        var fmt: Fmt? = null
        rule.setContent { CyclusTheme { fmt = rememberFmt(); TodayContent(state, fmt!!, PaddingValues(), null, {}, {}, {}, {}, {}, layout = allHidden) } }
        rule.onNodeWithText(fmt!!.s("notBC")).performScrollTo().assertExists()
        rule.onNodeWithText(fmt!!.s("whyLink")).performScrollTo().assertExists()
    }

    /** Drag regressions: every drag sees the current order (no stale order after the first drop), fast drags cross several rows. */
    @Test fun consecutiveDragsReorderAsExpected() {
        var fmt: Fmt? = null
        var layout by mutableStateOf(TodayLayout.DEFAULT)
        rule.setContent {
            CyclusTheme {
                fmt = rememberFmt()
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    TodayEditor(layout, fmt!!, onLayout = { layout = it }, onEditQuickPicks = {}, onDone = {})
                }
            }
        }
        val f = fmt!!
        fun handles() = rule.onAllNodesWithContentDescription(f.s("dragCard"), useUnmergedTree = true)
        fun centerY(i: Int) = handles()[i].fetchSemanticsNode().boundsInRoot.center.y
        /** Drag row [from] so its handle ends on row [to]'s handle, in small steps like a finger. */
        fun drag(from: Int, to: Int) {
            val dy = centerY(to) - centerY(from)
            handles()[from].performTouchInput {
                down(center)
                repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(0f, dy / 20)) }
                up()
            }
            rule.waitForIdle()
        }
        val start = layout.shown
        drag(3, 0)
        rule.runOnIdle { check(layout.shown == listOf(start[3], start[0], start[1], start[2]) + start.drop(4)) { "1: ${layout.shown}" } }
        val second = layout.shown
        drag(0, 2)
        rule.runOnIdle { check(layout.shown == listOf(second[1], second[2], second[0]) + second.drop(3)) { "2: ${layout.shown}" } }
        val third = layout.shown
        drag(5, 1)
        rule.runOnIdle { check(layout.shown == listOf(third[0], third[5], third[1], third[2], third[3], third[4]) + third.drop(6)) { "3: ${layout.shown}" } }
    }
}
