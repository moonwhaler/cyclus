package com.nordinary.cyclus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.store.AndroidDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class EncryptedStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun databaseFileIsEncryptedAndReopens(): Unit = runBlocking {
        val name = "encrypted-store-test.db"
        context.deleteDatabase(name)
        val log = DayLog(flow = Flow.HEAVY, symptoms = setOf("Cramps"), note = "secret note")
        AndroidDatabase.open(context, name).saveLog(20_000, log)

        val file = context.getDatabasePath(name)
        val header = file.inputStream().use { it.readNBytes(16) }.decodeToString()
        assertFalse(header.startsWith("SQLite format 3"), "database header is plaintext")
        assertFalse(file.readBytes().decodeToString().contains("secret note"), "note found in plaintext")

        // a second open (new driver, key unwrapped from the Keystore again) reads the same data
        assertEquals(log, AndroidDatabase.open(context, name).observeLogs().first()[20_000])
        context.deleteDatabase(name)
    }
}
