package com.nordinary.cyclus.insights

/**
 * Text produced by the engines: a string key plus arguments, rendered by each platform's UI from its own
 * localized resources (same keys as the prototype's `TX` table). Engines never produce display strings.
 *
 * Arguments are [Int], [String] (item/tag keys), [Num] (a number the UI formats with [Num.decimals]) or nested [Txt].
 */
data class Txt(val key: String, val args: List<Any> = emptyList(), val lowercase: Boolean = false)

data class Num(val value: Double, val decimals: Int)

internal fun txt(key: String, vararg args: Any) = Txt(key, args.toList())
