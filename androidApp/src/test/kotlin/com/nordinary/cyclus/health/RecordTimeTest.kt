package com.nordinary.cyclus.health

import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Health Connect rejects records in the future: day values and periods must never be stamped after "now". */
class RecordTimeTest {
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 11, 24)
    private val d = day.toEpochDay().toInt()
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant()
    private val noon = at(12)

    @Test fun todayBeforeNoonUsesNow() {
        val now = at(7, 30)
        val r = HealthSync.toRecord(OwnRecord("cyclus:bbt:$d", HealthType.BBT, d, "36.4"), 1, now = now) as BasalBodyTemperatureRecord
        assertEquals(now, r.time)
        assertEquals(Triple(HealthType.BBT, d, "36.4"), HealthSync.toCyclus(r))
    }

    @Test fun afternoonAndPastDaysStayAtNoon() {
        val r = HealthSync.toRecord(OwnRecord("cyclus:bbt:$d", HealthType.BBT, d, "36.4"), 1, now = at(18)) as BasalBodyTemperatureRecord
        assertEquals(noon, r.time)
        val past = HealthSync.toRecord(OwnRecord("cyclus:bbt:${d - 3}", HealthType.BBT, d - 3, "36.5"), 1, now = at(7)) as BasalBodyTemperatureRecord
        assertEquals(day.minusDays(3).atTime(12, 0).atZone(zone).toInstant(), past.time)
    }

    @Test fun ongoingPeriodEndsNowAndStillCoversToday() {
        val now = at(0, 40)
        val r = HealthSync.toRecord(OwnRecord("cyclus:period:${d - 2}", HealthType.PERIOD, d - 2, d.toString()), 1, now = now) as MenstruationPeriodRecord
        assertTrue(!r.endTime.isAfter(now))
        assertTrue(r.endTime.isAfter(r.startTime))
        assertEquals(Triple(HealthType.PERIOD, d - 2, d.toString()), HealthSync.toCyclus(r))
        // a period that started today, just after midnight: still a valid, non-empty record
        val fresh = HealthSync.toRecord(OwnRecord("cyclus:period:$d", HealthType.PERIOD, d, d.toString()), 1, now = at(0, 0)) as MenstruationPeriodRecord
        assertTrue(fresh.endTime.isAfter(fresh.startTime))
        assertEquals(Triple(HealthType.PERIOD, d, d.toString()), HealthSync.toCyclus(fresh))
    }
}
