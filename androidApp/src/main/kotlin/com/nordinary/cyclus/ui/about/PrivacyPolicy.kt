package com.nordinary.cyclus.ui.about

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.util.Locale

/**
 * The public privacy policy (GitHub Pages, built from PRIVACY.md / PRIVACY.de.md): German for German, English for every
 * other language. Opened in the browser; Cyclus itself stays without network access. Keep the URLs in step with
 * `.github/workflows/pages.yml` and the Play listing.
 */
object PrivacyPolicy {
    private const val BASE = "https://moonwhaler.github.io/cyclus"

    fun url(locale: Locale): String = if (locale.language == "de") "$BASE/de/datenschutz/" else "$BASE/privacy/"

    /** Opens the policy in the language the app shows. False when no app on the phone can open a web page. */
    fun open(ctx: Context): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, url(ctx.resources.configuration.locales[0]).toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
        if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }.isSuccess
}
