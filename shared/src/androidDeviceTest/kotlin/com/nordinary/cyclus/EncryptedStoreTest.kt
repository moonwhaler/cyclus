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
        val header = file.inputStream().use { s -> ByteArray(16).also { s.read(it) } }.decodeToString()
        assertFalse(header.startsWith("SQLite format 3"), "database header is plaintext")
        assertFalse(file.readBytes().decodeToString().contains("secret note"), "note found in plaintext")

        // a second open (new driver, key unwrapped from the Keystore again) reads the same data
        assertEquals(log, AndroidDatabase.open(context, name).observeLogs().first()[20_000])
        context.deleteDatabase(name)
    }
}

@RunWith(AndroidJUnit4::class)
class DbKeyRuleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Android 10–14 rule ⇄ Android 15+ rule: the same database key survives every re-wrap (OS upgrade, this fix). */
    @Test fun wrappedKeyMovesBetweenRulesWithoutChanging() {
        // the strict rule needs Android 15+ or a screen lock (exactly what the fix is about)
        org.junit.Assume.assumeTrue(
            android.os.Build.VERSION.SDK_INT >= 35 || context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure,
        )
        val name = "kek-rule-test.db"
        val file = java.io.File(context.noBackupFilesDir, "$name.key")
        com.nordinary.cyclus.store.DbKeyManager(context, name).destroy()
        val k = com.nordinary.cyclus.store.DbKeyManager(context, name, strict = true).obtainKey(databaseExists = false)
        assertEquals(1, file.readBytes()[0].toInt())
        // an install from before the fix, opened on Android 10–14: re-wrapped without the unlocked-device rule
        assertEquals(k.toList(), com.nordinary.cyclus.store.DbKeyManager(context, name, strict = false).obtainKey(true).toList())
        assertEquals(2, file.readBytes()[0].toInt())
        assertEquals(k.toList(), com.nordinary.cyclus.store.DbKeyManager(context, name, strict = false).obtainKey(true).toList())
        // after an upgrade to Android 15+: back to the stricter rule, same key
        assertEquals(k.toList(), com.nordinary.cyclus.store.DbKeyManager(context, name, strict = true).obtainKey(true).toList())
        assertEquals(1, file.readBytes()[0].toInt())
        com.nordinary.cyclus.store.DbKeyManager(context, name).destroy()
    }
}
