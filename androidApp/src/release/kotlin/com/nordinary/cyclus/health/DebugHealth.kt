package com.nordinary.cyclus.health

import android.content.Context

/** Release: the debug menu (and its Health Connect test data) doesn't exist. */
object DebugHealth {
    @Suppress("UNUSED_PARAMETER")
    suspend fun writeExternal(ctx: Context) = Unit
}
