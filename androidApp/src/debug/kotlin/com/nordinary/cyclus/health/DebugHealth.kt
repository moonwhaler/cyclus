package com.nordinary.cyclus.health

import android.content.Context
import java.time.LocalDate

/** Debug only: writes records that Cyclus treats as another app's (no `cyclus:` prefix), to test the import path. */
object DebugHealth {
    suspend fun writeExternal(ctx: Context) {
        val today = LocalDate.now().toEpochDay().toInt()
        val hc = HealthSync(ctx)
        val records = listOf(
            OwnRecord("ext:bbt:$today", HealthType.BBT, today, "36.72"),
            OwnRecord("ext:lh:${today - 1}", HealthType.LH, today - 1, "pos"),
            OwnRecord("ext:flow:${today - 2}", HealthType.FLOW, today - 2, "light"),
            OwnRecord("ext:mucus:$today", HealthType.MUCUS, today, "eggwhite"),
        )
        hc.client.insertRecords(records.map { HealthSync.toRecord(it, System.currentTimeMillis(), clientId = it.syncId) })
    }
}
