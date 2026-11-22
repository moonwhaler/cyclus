package com.nordinary.cyclus.store

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.nordinary.cyclus.store.db.CyclusDatabase
import kotlinx.coroutines.Dispatchers
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Opens the SQLCipher-encrypted store. Never deletes or recreates an existing database: if the key can't be
 * provided, [open] throws [DbKeyException] and the app shows a blocking error instead.
 * Call off the main thread.
 */
object AndroidDatabase {
    const val FILE_NAME = "cyclus.db"

    fun open(context: Context, fileName: String = FILE_NAME): CyclusRepository {
        val app = context.applicationContext
        System.loadLibrary("sqlcipher")
        val dbFile = app.getDatabasePath(fileName)
        val key = DbKeyManager(app, fileName).obtainKey(databaseExists = dbFile.exists())
        // Raw-key form x'…' skips SQLCipher's PBKDF2 (the key is already 256 random bits).
        // built byte by byte (no String): every copy of the key can be wiped
        val passphrase = rawKeyPassphrase(key)
        key.fill(0)
        val factory = SupportOpenHelperFactory(passphrase, null, true)
        val driver = AndroidSqliteDriver(CyclusDatabase.Schema, app, fileName, factory)
        return CyclusRepository(CyclusDatabase(driver), Dispatchers.IO, onClose = { driver.close(); passphrase.fill(0) }) { System.currentTimeMillis() }
    }

    fun exists(context: Context, fileName: String = FILE_NAME): Boolean = context.applicationContext.getDatabasePath(fileName).exists()

    /**
     * Crypto-shred: deletes the database files (incl. WAL/SHM/journal) **and** the key that encrypted them (wrapped
     * key file + Keystore alias). Only call after the repository's driver is closed.
     */
    fun destroy(context: Context, fileName: String = FILE_NAME) {
        val app = context.applicationContext
        app.deleteDatabase(fileName)
        DbKeyManager(app, fileName).destroy()
    }

    /** SQLCipher raw-key literal `x'<64 hex>'` as ASCII bytes. */
    internal fun rawKeyPassphrase(key: ByteArray): ByteArray {
        val hex = "0123456789abcdef"
        val out = ByteArray(3 + key.size * 2)
        out[0] = 'x'.code.toByte(); out[1] = '\''.code.toByte(); out[out.size - 1] = '\''.code.toByte()
        for ((i, b) in key.withIndex()) {
            val v = b.toInt() and 0xFF
            out[2 + i * 2] = hex[v ushr 4].code.toByte(); out[3 + i * 2] = hex[v and 0xF].code.toByte()
        }
        return out
    }
}
