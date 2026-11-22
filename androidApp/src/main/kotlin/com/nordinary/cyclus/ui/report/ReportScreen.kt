package com.nordinary.cyclus.ui.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordinary.cyclus.CyclusApplication
import com.nordinary.cyclus.R
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.presenter.ReportSection
import com.nordinary.cyclus.report.ReportFiles
import com.nordinary.cyclus.ui.AppViewModel
import com.nordinary.cyclus.ui.CyclusSnackbarHost
import com.nordinary.cyclus.ui.common.CyclusIcons
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.today.PrimaryButton
import com.nordinary.cyclus.ui.today.SecondaryButton
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Arztbericht builder (prototype `renderReport`) with a preview of the real PDF. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReportScreen(vm: AppViewModel, fmt: Fmt, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val c = Cyclus.colors
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CyclusApplication
    val uiLocale = LocalConfiguration.current.locales[0]
    val defaultQ = fmt.s("qDefault")
    val shareTitle = stringResource(R.string.rep_share_title)
    LaunchedEffect(Unit) { vm.report.start(defaultQ, uiLocale) }
    val options by vm.report.options.collectAsStateWithLifecycle()
    val file by vm.report.file.collectAsStateWithLifecycle()
    val working by vm.report.working.collectAsStateWithLifecycle()
    val pdfLocale by vm.report.locale.collectAsStateWithLifecycle()
    val pdfDe = pdfLocale.language == "de"
    val o = options ?: return
    val back = { vm.report.close(); onBack() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.bg,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(fmt.s("reportT"), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = back) { Icon(CyclusIcons.Back, fmt.s("back"), tint = c.accent) } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, scrolledContainerColor = c.bg),
            )
        },
        snackbarHost = { CyclusSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text(fmt.s("repIntro"), color = c.ink2, fontSize = 14.sp) }
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("${fmt.s("timeSpan")} · ${fmt.s("cyclesW")}")
                Segments(listOf(3, 6, 12).map { fmt.s("lastN", it) }, listOf(3, 6, 12).indexOf(o.cycles)) { i ->
                    vm.report.update { it.copy(cycles = listOf(3, 6, 12)[i]) }
                }
            } }
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(fmt.s("include"))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)) {
                    ReportSection.entries.forEachIndexed { i, sec ->
                        if (i > 0) HorizontalDivider(color = c.line)
                        val on = sec in o.include
                        Row(
                            Modifier.fillMaxWidth().toggleable(on, role = Role.Switch) { v ->
                                vm.report.update { it.copy(include = if (v) it.include + sec else it.include - sec) }
                            }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(fmt.s("inc_${sec.key}"), color = c.ink, fontWeight = FontWeight.Medium)
                                if (sec.sensitive) Text(fmt.s("offDefault"), fontSize = 12.sp, color = c.ink2)
                            }
                            Switch(on, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.success))
                        }
                    }
                }
            } }
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(androidx.compose.ui.res.stringResource(R.string.rep_name_field))
                OutlinedTextField(
                    o.name, { n -> vm.report.update { it.copy(name = n.take(80)) } }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(androidx.compose.ui.res.stringResource(R.string.rep_name_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.line, focusedBorderColor = c.accent, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface),
                )
                Text(androidx.compose.ui.res.stringResource(R.string.rep_name_note), fontSize = 12.sp, color = c.ink2)
            } }
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(fmt.s("questions"))
                OutlinedTextField(
                    o.questions, { q -> vm.report.update { it.copy(questions = q) } }, Modifier.fillMaxWidth(), minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.line, focusedBorderColor = c.accent, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface),
                )
            } }
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(fmt.s("pdfLang"))
                Segments(listOf("Deutsch", "English"), if (pdfDe) 0 else 1) { i ->
                    vm.report.locale.value = if (i == 0) Locale.GERMAN else Locale.ENGLISH
                }
            } }
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (working) {
                    Text(stringResource(R.string.rep_working), color = c.ink2)
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                } else if (file == null) PrimaryButton(fmt.s("previewBtn"), { vm.report.build(uiLocale) }, Modifier.fillMaxWidth())
                else SecondaryButton(fmt.s("updatePrev"), { vm.report.build(uiLocale) }, Modifier.fillMaxWidth())
            } }
            val f = file
            if (f != null && !working) {
                item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(fmt.s("sharePdf"), {
                        app.lock.suspendForExternalActivity()
                        ctx.startActivity(ReportFiles.shareIntent(ctx, f, shareTitle))
                    }, Modifier.fillMaxWidth())
                    Text(stringResource(R.string.rep_share_note), fontSize = 12.sp, color = c.ink2)
                } }
                pdfPages(f)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** Renders the real PDF page by page, lazily, at screen width (one renderer per file). */
private fun LazyListScope.pdfPages(file: File) {
    item(key = file.path) {
        val c = Cyclus.colors
        val renderer = remember(file) { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }
        val lock = remember(file) { Mutex() }
        DisposableEffect(renderer) { onDispose { runCatching { renderer.close() } } }
        val count = renderer.pageCount
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (i in 0 until count) {
                Text(stringResource(R.string.rep_preview_page, i + 1, count), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.ink2)
                PdfPage(renderer, lock, i)
            }
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, lock: Mutex, index: Int) {
    val c = Cyclus.colors
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        var bmp by remember(renderer, index) { mutableStateOf<Bitmap?>(null) }
        var ratio by remember(renderer, index) { mutableStateOf(842f / 595f) }
        LaunchedEffect(renderer, index, widthPx) {
            bmp = withContext(Dispatchers.Default) {
                lock.withLock {
                    renderer.openPage(index).use { p ->
                        ratio = p.height.toFloat() / p.width
                        Bitmap.createBitmap(widthPx, (widthPx * ratio).toInt(), Bitmap.Config.ARGB_8888).also { b ->
                            b.eraseColor(Color.WHITE)
                            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
        DisposableEffect(bmp) { val b = bmp; onDispose { b?.recycle() } }
        Box(Modifier.fillMaxWidth().aspectRatio(1f / ratio).clip(RoundedCornerShape(6.dp)).border(1.dp, c.line, RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color.White)) {
            bmp?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().semantics { contentDescription = "PDF ${index + 1}" }) }
        }
    }
}

@Composable
private fun Label(t: String) = Text(t.uppercase(), Modifier.padding(horizontal = 4.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = Cyclus.colors.ink2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Cyclus.colors
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, l ->
            SegmentedButton(
                selected = selected == i, onClick = { onSelect(i) }, shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = c.accent.copy(alpha = .14f), activeContentColor = c.ink, inactiveContainerColor = c.surface),
            ) { Text(l, fontWeight = FontWeight.SemiBold) }
        }
    }
}
