package com.nordinary.cyclus.security

import com.nordinary.cyclus.backup.CyclusCrypto

/**
 * Cyclus PIN (6 digits). A **UI gate** against people who know the device's screen lock: the hash is stored inside the
 * encrypted database, so it adds no cryptographic protection to the data itself.
 */
object Pin {
    const val LENGTH = 6
    private const val ITERATIONS = 200_000

    fun isValid(pin: String) = pin.length == LENGTH && pin.all { it in '0'..'9' }

    /** Returns `salt` and `hash`, both hex. */
    fun hash(pin: String, salt: ByteArray = CyclusCrypto.random(16)): Pair<String, String> {
        require(isValid(pin))
        return salt.hex() to CyclusCrypto.pbkdf2(pin.toCharArray(), salt, ITERATIONS).hex()
    }

    fun verify(pin: String, saltHex: String, hashHex: String): Boolean {
        if (!isValid(pin)) return false
        val h = CyclusCrypto.pbkdf2(pin.toCharArray(), saltHex.unhex(), ITERATIONS)
        val expected = hashHex.unhex()
        var diff = h.size xor expected.size
        for (i in 0 until minOf(h.size, expected.size)) diff = diff or (h[i].toInt() xor expected[i].toInt())
        return diff == 0 // constant time
    }

    /** Lockout after 5 wrong tries: 30 s, 1 min, 5 min, 15 min, then 1 h per further try. */
    fun backoffMillis(failures: Int): Long = when {
        failures < 5 -> 0
        failures == 5 -> 30_000
        failures == 6 -> 60_000
        failures == 7 -> 300_000
        failures == 8 -> 900_000
        else -> 3_600_000
    }

    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
