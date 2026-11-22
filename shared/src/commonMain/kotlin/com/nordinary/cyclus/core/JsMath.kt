package com.nordinary.cyclus.core

import kotlin.math.floor

/**
 * Helpers that reproduce JavaScript semantics where Kotlin differs. The engines are ported 1:1 from the prototype
 * (the behavioural source of truth) and are checked against golden vectors, so these must match exactly.
 */
object Js {
    /** JS `Math.round`: halves round towards +∞ (Kotlin's `round` rounds halves to even). */
    fun round(x: Double): Double = floor(x + 0.5)

    fun roundInt(x: Double): Int = round(x).toInt()

    /** JS `Math.sign` for doubles (keeps ±0 and NaN). */
    fun sign(x: Double): Double = kotlin.math.sign(x)
}

/** mulberry32, identical to the prototype's `rng(seed)` (same 32-bit integer arithmetic as `Math.imul` / `>>>`). */
class Rng(seed: Int) {
    private var state = seed

    fun next(): Double {
        state += 0x6D2B79F5
        val s = state
        var x = (s xor (s ushr 15)) * (1 or s)
        x = (x + ((x xor (x ushr 7)) * (61 or x))) xor x
        val u = (x xor (x ushr 14)).toLong() and 0xFFFFFFFFL
        return u.toDouble() / 4294967296.0
    }
}
