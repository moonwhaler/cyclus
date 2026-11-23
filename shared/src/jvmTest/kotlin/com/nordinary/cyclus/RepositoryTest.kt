package com.nordinary.cyclus

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.nordinary.cyclus.backup.ExportFile
import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.core.CycleOverride
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Episode
import com.nordinary.cyclus.core.EpisodeKind
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.core.Mucus
import com.nordinary.cyclus.core.OverrideKind
import com.nordinary.cyclus.presenter.DoctorQuestion
import com.nordinary.cyclus.presenter.DoctorQuestions
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import com.nordinary.cyclus.store.CyclusRepository
import com.nordinary.cyclus.store.db.CyclusDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {
    private fun repo(now: Long = 1_000L): CyclusRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CyclusDatabase.Schema.create(driver)
        return CyclusRepository(CyclusDatabase(driver), Dispatchers.Unconfined) { now }
    }

    @Test fun exportNeverCarriesSecuritySettings(): Unit = runBlocking {
        val r = repo()
        r.saveLog(20_000, full)
        r.setSetting(SettingKeys.MODE, "conceive")
        r.setSetting(SettingKeys.LOCK_PIN_HASH, "secret-hash")
        r.setSetting(SettingKeys.LOCK_ENABLED, "1")
        r.setSetting(SettingKeys.EXPORT_COUNT, "3")
        val user = r.exportAll("t", "test", includeSecurity = false)
        assertEquals(mapOf(SettingKeys.MODE to "conceive"), user.prefs)
        assertNull(user.security)
        val bytes = user.encode().decodeToString()
        assertTrue("secret-hash" !in bytes && "export.count" !in bytes)
        val snapshot = r.exportAll("t", "test", includeSecurity = true)
        assertEquals(mapOf(SettingKeys.LOCK_PIN_HASH to "secret-hash", SettingKeys.LOCK_ENABLED to "1"), snapshot.security)
    }

    @Test fun exportImportRoundTripsEverything(): Unit = runBlocking {
        val a = repo()
        a.saveLog(20_000, full)
        a.saveLog(20_001, DayLog(flow = Flow.SPOTTING))
        a.setOverride(20_000, CycleOverride(OverrideKind.START))
        a.saveEpisode(Episode("e1", EpisodeKind.PREGNANCY, 19_000, 19_100))
        a.setSetting(SettingKeys.MUTED, "it-Cramps")
        val file = ExportFile.decode(a.exportAll("t", "test", false).encode())
        val b = repo()
        b.importReplace(file)
        assertEquals(a.observeLogs().first(), b.observeLogs().first())
        assertEquals(a.observeOverrides().first(), b.observeOverrides().first())
        assertEquals(a.observeEpisodes().first(), b.observeEpisodes().first())
        assertEquals(mapOf(SettingKeys.MUTED to "it-Cramps"), b.observeSettings().first())
    }

    @Test fun replaceKeepsLocalSecurityAndDeviceSettings(): Unit = runBlocking {
        val b = repo()
        b.saveLog(19_000, full)
        b.setSetting(SettingKeys.LOCK_PIN_HASH, "mine")
        b.setSetting(SettingKeys.EXPORT_COUNT, "2")
        b.setSetting(SettingKeys.MODE, "conceive")
        b.importReplace(com.nordinary.cyclus.backup.ExportFile(exportedAt = "t", app = "x", prefs = mapOf(SettingKeys.SHOW_FERT to "1")))
        assertTrue(b.observeLogs().first().isEmpty())
        val s = b.observeSettings().first()
        assertEquals("mine", s[SettingKeys.LOCK_PIN_HASH]); assertEquals("2", s[SettingKeys.EXPORT_COUNT])
        assertEquals("1", s[SettingKeys.SHOW_FERT]); assertNull(s[SettingKeys.MODE])
    }

    @Test fun mergeKeepsNewerDayAndLocalPrefs(): Unit = runBlocking {
        val old = repo(now = 100)
        old.saveLog(20_000, DayLog(mood = 1)); old.saveLog(20_001, DayLog(mood = 1))
        val newer = repo(now = 200)
        newer.saveLog(20_000, DayLog(mood = 5)); newer.saveLog(20_002, DayLog(mood = 3))
        newer.setSetting(SettingKeys.MODE, "conceive")
        old.setSetting(SettingKeys.MODE, "track")
        old.importMerge(newer.exportAll("t", "x", false))
        val logs = old.observeLogs().first()
        assertEquals(5, logs[20_000]?.mood) // newer wins
        assertEquals(1, logs[20_001]?.mood) // untouched
        assertEquals(3, logs[20_002]?.mood) // added
        assertEquals("track", old.observeSettings().first()[SettingKeys.MODE]) // local pref kept
        // merging the older data back changes nothing
        newer.importMerge(old.exportAll("t", "x", false))
        assertEquals(5, newer.observeLogs().first()[20_000]?.mood)
    }

    @Test fun mergeImportUnitesDoctorQuestions(): Unit = runBlocking {
        val a = repo(now = 5_000); val b = repo(now = 5_000)
        a.setSetting(SettingKeys.DOCTOR_QUESTIONS, DoctorQuestions.encode(listOf(
            DoctorQuestion("1", "Mine", at = 100), DoctorQuestion("2", "Shared old", at = 100),
        )))
        b.setSetting(SettingKeys.DOCTOR_QUESTIONS, DoctorQuestions.encode(listOf(
            DoctorQuestion("2", "Shared new", done = true, at = 200), DoctorQuestion("3", "Theirs", at = 99_999_999),
        )))
        a.importMerge(b.exportAll("t", "x", false))
        val merged = DoctorQuestions.from(a.observeSettings().first())
        assertEquals(setOf("1", "2", "3"), merged.map { it.id }.toSet())
        assertEquals("Shared new", merged.single { it.id == "2" }.text) // newer wins
        assertEquals(5_000, merged.single { it.id == "3" }.at) // a clock from the future counts as now
        // a file without questions leaves them alone; replace takes the file's list
        a.importMerge(repo().exportAll("t", "x", false))
        assertEquals(3, DoctorQuestions.from(a.observeSettings().first()).size)
        a.importReplace(b.exportAll("t", "x", false))
        assertEquals(setOf("2", "3"), DoctorQuestions.from(a.observeSettings().first()).map { it.id }.toSet())
    }

    private val full = DayLog(
        flow = Flow.UNSPECIFIED, symptoms = setOf("Cramps", "Breast tenderness"), symptomSeverity = mapOf("Cramps" to 3),
        pain = mapOf("lowC" to 7, "head" to 0), emotions = setOf("Calm", "Sad"), tags = setOf("Stress", "user-tag-uuid"),
        mood = 2, energy = 5, sleep = 1, libido = 4, mucus = Mucus.EGGWHITE, lh = LhResult.SURGE, bbt = 36.47,
        sex = true, sexProtected = false, note = "Notiz mit Umlauten: äöü ß", nothingElse = true,
    )

    @Test fun roundTripsEveryField(): Unit = runBlocking {
        val r = repo()
        r.saveLog(20_000, full)
        r.saveLog(20_001, DayLog(flow = Flow.SPOTTING))
        val logs = r.observeLogs().first()
        assertEquals(full, logs[20_000])
        assertEquals(DayLog(flow = Flow.SPOTTING), logs[20_001])
    }

    @Test fun overwritingReplacesChildren(): Unit = runBlocking {
        val r = repo()
        r.saveLog(20_000, full)
        val smaller = full.copy(symptoms = setOf("Headache"), symptomSeverity = emptyMap(), pain = emptyMap(), emotions = emptySet(), tags = emptySet())
        r.saveLog(20_000, smaller)
        assertEquals(smaller, r.log(20_000))
    }

    @Test fun emptyLogIsDeleted(): Unit = runBlocking {
        val r = repo()
        r.saveLog(20_000, full)
        r.saveLog(20_000, DayLog())
        assertNull(r.log(20_000))
        assertTrue(r.observeLogs().first().isEmpty())
    }

    @Test fun overridesEpisodesSettingsAndErase(): Unit = runBlocking {
        val r = repo()
        r.setOverride(20_000, CycleOverride(OverrideKind.START))
        r.setOverride(20_010, CycleOverride(OverrideKind.EXCLUDE, "illness"))
        r.saveEpisode(Episode("e1", EpisodeKind.HORMONAL_CONTRACEPTION, 19_900, null))
        r.setSetting("mode", "conceive")
        assertEquals(
            mapOf(20_000 to CycleOverride(OverrideKind.START), 20_010 to CycleOverride(OverrideKind.EXCLUDE, "illness")),
            r.observeOverrides().first(),
        )
        assertEquals(listOf(Episode("e1", EpisodeKind.HORMONAL_CONTRACEPTION, 19_900, null)), r.observeEpisodes().first())
        assertEquals(mapOf("mode" to "conceive"), r.observeSettings().first())
        r.setOverride(20_000, null)
        assertEquals(setOf(20_010), r.observeOverrides().first().keys)
        r.saveLog(20_000, full)
        r.eraseAll()
        assertTrue(r.observeLogs().first().isEmpty())
        assertTrue(r.observeOverrides().first().isEmpty())
        assertTrue(r.observeEpisodes().first().isEmpty())
        assertTrue(r.observeSettings().first().isEmpty())
    }

    @Test fun sampleScenarioSurvivesStorage(): Unit = runBlocking {
        val r = repo()
        val sample = SampleScenarios.generate(SampleScenario.CONCEIVE_2WW, 20_779)
        r.replaceAll(sample.logs)
        assertEquals(sample.logs.filterValues { !it.isEmpty }, r.observeLogs().first())
    }
}

class RepositoryUpdateTest {
    @Test fun concurrentUpdatesOfOneDayAreNeverLost(): Unit = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CyclusDatabase.Schema.create(driver)
        val r = CyclusRepository(CyclusDatabase(driver), Dispatchers.IO) { 1L }
        val symptoms = com.nordinary.cyclus.core.Catalog.SYMPTOMS
        kotlinx.coroutines.coroutineScope {
            for (s in symptoms) launch { r.updateLog(20_000) { it.copy(symptoms = it.symptoms + s) } }
        }
        assertEquals(symptoms.toSet(), r.log(20_000)?.symptoms)
        // the previous state comes back for undo; an unchanged transform writes nothing
        assertEquals(symptoms.toSet(), r.updateLog(20_000) { it }?.symptoms)
        assertNull(r.updateLog(20_001) { it })
        assertNull(r.log(20_001))
    }
}
