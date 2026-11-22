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
        val key = DbKeyManager(app).obtainKey(databaseExists = dbFile.exists())
        // Raw-key form x'…' skips SQLCipher's PBKDF2 (the key is already 256 random bits).
        val passphrase = "x'${key.toHex()}'".toByteArray(Charsets.US_ASCII)
        key.fill(0)
        val factory = SupportOpenHelperFactory(passphrase, null, true)
        val driver = AndroidSqliteDriver(CyclusDatabase.Schema, app, fileName, factory)
        return CyclusRepository(CyclusDatabase(driver), Dispatchers.IO) { System.currentTimeMillis() }
    }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
