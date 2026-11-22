package com.nordinary.cyclus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.i18n.rememberFmt
import com.nordinary.cyclus.insights.Area
import com.nordinary.cyclus.presenter.InsightsPresenter
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import com.nordinary.cyclus.ui.insights.PatternSummaryCard
import com.nordinary.cyclus.ui.insights.PatternsScreen
import com.nordinary.cyclus.ui.theme.CyclusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Insights revamp edge cases: hidden patterns stay restorable; a filter whose area disappears falls back to „Alle“. */
@RunWith(AndroidJUnit4::class)
class PatternsUiTest {
    @get:Rule val rule = createComposeRule()
    private val snap = run {
        val today = LocalDate.now().toEpochDay().toInt()
        val d = SampleScenarios.generate(SampleScenario.DAY12, today)
        CycleSnapshot.compute(CycleData(d.logs, emptyMap(), today), d.mode)
    }
    private val allIds = InsightsPresenter.patternSection(snap, emptySet()).let { s -> (s.found + s.watching).map { it.id }.toSet() }

    @Test fun everythingHiddenStillLeadsToRestore() {
        var fmt: Fmt? = null
        var all = false
        val sec = InsightsPresenter.patternSection(snap, allIds)
        rule.setContent { CyclusTheme { fmt = rememberFmt(); PatternSummaryCard(sec, fmt!!, onOpen = {}, onAll = { all = true }) } }
        rule.onNodeWithText(fmt!!.s("hiddenN", sec.hiddenCount)).performClick()
        rule.runOnIdle { assert(all) }
    }

    @Test fun filterFallsBackWhenItsAreaDisappears() {
        var fmt: Fmt? = null
        var muted by mutableStateOf(emptySet<String>())
        rule.setContent {
            CyclusTheme {
                fmt = rememberFmt()
                PatternsScreen(InsightsPresenter.patternSection(snap, muted), fmt!!, onBack = {}, onMute = { muted = muted + it }, onUnmute = { muted = emptySet() })
            }
        }
        val found = InsightsPresenter.patternSection(snap, emptySet()).found
        val life = found.filter { it.area == Area.LIFE }
        check(life.isNotEmpty() && found.size > life.size)
        rule.onNodeWithText(fmt!!.s("area_${Area.LIFE.key}")).performClick()
        rule.onAllNodesWithText(fmt!!.txt(found.first { it.area != Area.LIFE }.title)).assertCountEquals(0)
        rule.runOnIdle { muted = life.map { it.id }.toSet() } // hide the last patterns of the selected area
        rule.onAllNodesWithText(fmt!!.s("area_${Area.LIFE.key}")).assertCountEquals(0) // chip gone
        rule.onNodeWithText(fmt!!.txt(found.first { it.area != Area.LIFE }.title)).assertExists() // back to „Alle“
        rule.onNodeWithText(fmt!!.s("hiddenN", life.size)).performScrollTo().performClick()
        rule.runOnIdle { assert(muted.isEmpty()) }
    }
}
