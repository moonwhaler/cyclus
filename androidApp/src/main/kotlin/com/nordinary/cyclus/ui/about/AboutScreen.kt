package com.nordinary.cyclus.ui.about

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.aboutlibraries.Libs
import com.nordinary.cyclus.BuildConfig
import com.nordinary.cyclus.R
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.ui.common.CyclusIcons
import com.nordinary.cyclus.ui.theme.Cyclus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One bundled open-source component and its license texts (generated at build time, see build.gradle.kts). */
data class OssLib(val id: String, val name: String, val version: String, val website: String?, val licenses: List<OssLicense>)
data class OssLicense(val name: String, val url: String?, val text: String)

object OpenSource {
    @Volatile private var cache: List<OssLib>? = null

    /** Parses the generated aboutlibraries.json once; off the main thread (≈ 100 entries with full license texts). */
    suspend fun load(ctx: Context): List<OssLib> = cache ?: withContext(Dispatchers.IO) {
        val json = ctx.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        Libs.Builder().withJson(json).build().libraries
            .map { lib ->
                OssLib(
                    id = lib.uniqueId, name = lib.name.ifBlank { lib.uniqueId }, version = lib.artifactVersion.orEmpty(),
                    website = lib.website?.takeIf { it.isNotBlank() },
                    licenses = lib.licenses
                        .filter { it.name.isNotBlank() || !it.licenseContent.isNullOrBlank() } // drops unnamed, empty stubs
                        .map { OssLicense(it.name, it.url?.takeIf { u -> u.isNotBlank() }, reflow(it.licenseContent.orEmpty().replace("<br />", ""))) },
                )
            }
            .sortedBy { it.name.lowercase() }
            .also { cache = it }
    }
}

private val LIST_ITEM = Regex("""^(\*|-|•|\(?[0-9A-Za-z]{1,3}[.)])\s.*""")

/**
 * License files are hard-wrapped at ~80 columns, which breaks mid-sentence on a phone: join the lines of each
 * paragraph, keep blank lines between paragraphs and start list items („1.“, „(a)“, „*“) on their own line.
 */
internal fun reflow(text: String): String =
    text.replace("\r\n", "\n").trim().split(Regex("""\n\s*\n""")).joinToString("\n\n") { para ->
        val out = StringBuilder()
        for (raw in para.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (out.isNotEmpty()) out.append(if (LIST_ITEM.matches(line)) "\n" else " ")
            out.append(line)
        }
        out.toString()
    }

/** Cyclus's own license (GPL-3.0), copied from the repository's LICENSE into the assets at build time. */
object AppLicense {
    @Volatile private var cache: String? = null

    suspend fun load(ctx: Context): String = cache ?: withContext(Dispatchers.IO) {
        reflow(ctx.assets.open("LICENSE.txt").bufferedReader().use { it.readText() }).also { cache = it }
    }
}

@Composable
private fun rememberOpenSource(): State<List<OssLib>?> {
    val ctx = LocalContext.current
    return produceState<List<OssLib>?>(null) { value = OpenSource.load(ctx) }
}

/** „Über Cyclus“: name, version, the notice with its confirmations, how predictions work, the app's license, open-source licenses. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    fmt: Fmt,
    consentLine: String,
    onBack: () -> Unit,
    onNotice: () -> Unit,
    onMethod: () -> Unit,
    onLicenses: () -> Unit,
) {
    val c = Cyclus.colors
    val libs by rememberOpenSource()
    var showLicense by rememberSaveable { mutableStateOf(false) }
    PushedScaffold(fmt, fmt.s("aboutT"), onBack) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                modifier = Modifier.size(88.dp).clip(CircleShape).background(colorResource(R.color.ic_launcher_background)),
            )
            Text("Cyclus", Modifier.padding(top = 8.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text(fmt.s("aboutVer", BuildConfig.VERSION_NAME), fontSize = 14.sp, color = c.ink2)
            Text(fmt.s("onb0B"), Modifier.padding(top = 6.dp), fontSize = 14.sp, color = c.ink2)
        }
        Group {
            LinkRow(fmt.s("noticeRow"), consentLine, onNotice)
            HorizontalDivider(color = c.line)
            LinkRow(fmt.s("methodT"), null, onMethod)
            HorizontalDivider(color = c.line)
            LinkRow(fmt.s("ossLic"), fmt.s("licS")) { showLicense = true }
            HorizontalDivider(color = c.line)
            LinkRow(fmt.s("ossT"), libs?.let { fmt.s("ossS", it.size) }, onLicenses)
        }
    }
    if (showLicense) {
        val ctx = LocalContext.current
        val text by produceState<String?>(null) { value = AppLicense.load(ctx) }
        val gpl = OssLib("cyclus", "Cyclus", BuildConfig.VERSION_NAME, null, listOf(OssLicense("GNU General Public License v3.0", null, text.orEmpty())))
        LicenseSheet(fmt, gpl, intro = "${fmt.s("licNote")}\n\n${fmt.s("licCopy")}") { showLicense = false }
    }
}

/** „Open-Source-Lizenzen“: every bundled component; tap one for its license text. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LicensesScreen(fmt: Fmt, onBack: () -> Unit) {
    val c = Cyclus.colors
    val libs by rememberOpenSource()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.bg,
        topBar = { TopBar(fmt, fmt.s("ossT"), onBack, scroll) },
    ) { padding ->
        val list = libs.orEmpty()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item { Text(fmt.s("ossIntro"), Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp), fontSize = 13.sp, color = c.ink2) }
            itemsIndexed(list, key = { _, l -> l.id }) { i, lib ->
                val shape = when {
                    list.size == 1 -> RoundedCornerShape(14.dp)
                    i == 0 -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                    i == list.lastIndex -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Column(Modifier.fillMaxWidth().clip(shape).background(c.surface)) {
                    if (i > 0) HorizontalDivider(color = c.line)
                    LinkRow(lib.name, listOf(lib.version, lib.licenses.joinToString { it.name }).filter { it.isNotBlank() }.joinToString(" · ")) { openId = lib.id }
                }
            }
        }
    }
    list@ for (lib in libs.orEmpty()) if (lib.id == openId) { LicenseSheet(fmt, lib) { openId = null }; break@list }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseSheet(fmt: Fmt, lib: OssLib, intro: String? = null, onDismiss: () -> Unit) {
    val c = Cyclus.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lib.name, Modifier.weight(1f).semantics { heading() }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = onDismiss) { Text(fmt.s("close"), color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
        SelectionContainer {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val meta = listOfNotNull(lib.version.takeIf { it.isNotBlank() }, lib.website).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, fontSize = 13.sp, color = c.ink2)
                if (intro != null) Text(intro, fontSize = 14.sp, color = c.ink)
                for (l in lib.licenses) {
                    Text("${fmt.s("ossLic")}: ${l.name}", Modifier.padding(top = 6.dp), fontWeight = FontWeight.SemiBold, color = c.ink)
                    if (l.text.isNotBlank()) Text(
                        l.text, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).padding(12.dp),
                        fontSize = 13.sp, lineHeight = 18.sp, color = c.ink,
                    ) else if (l.url != null) Text(l.url, fontSize = 13.sp, color = c.ink2)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PushedScaffold(fmt: Fmt, title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Cyclus.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.bg,
        topBar = { TopBar(fmt, title, onBack, scroll) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(fmt: Fmt, title: String, onBack: () -> Unit, scroll: androidx.compose.material3.TopAppBarScrollBehavior) {
    val c = Cyclus.colors
    LargeFlexibleTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(CyclusIcons.Back, fmt.s("back"), tint = c.accent) } },
        scrollBehavior = scroll,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, scrolledContainerColor = c.bg),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cyclus.colors.surface)) { content() }
}

@Composable
private fun LinkRow(title: String, sub: String?, onClick: () -> Unit) {
    val c = Cyclus.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = c.ink)
            if (!sub.isNullOrBlank()) Text(sub, fontSize = 13.sp, color = c.ink2)
        }
        Text("›", color = c.ink2, fontSize = 18.sp)
    }
}
