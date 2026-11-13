package app.ofiofi.docxdocx

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ofiofi.docxdocx.canvas.DocumentCanvas
import app.ofiofi.docxdocx.canvas.DocumentLayoutMode
import app.ofiofi.docxdocx.input.KeyboardShortcutRegistry
import app.ofiofi.docxdocx.input.SessionDocumentTextSink
import app.ofiofi.docxdocx.input.WordInputBridge
import app.ofiofi.docxdocx.input.WordInputHost
import app.ofiofi.docxdocx.input.WordInputView
import app.ofiofi.docxdocx.print.DocxPrintDocumentAdapter
import app.ofiofi.docxdocx.storage.RecentDocEntry
import app.ofiofi.docxdocx.storage.RecentDocumentStore
import app.ofiofi.docxdocx.storage.SafDocumentHelper
import app.ofiofi.docxdocx.storage.StorageProviderKind
import app.ofiofi.docxdocx.ui.backstage.BackstageArchivoOverlay
import app.ofiofi.docxdocx.ui.canvas.HorizontalPageRuler
import app.ofiofi.docxdocx.ui.canvas.RulerMath
import app.ofiofi.docxdocx.ui.canvas.RulerViewportMetrics
import app.ofiofi.docxdocx.ui.canvas.VerticalPageRuler
import app.ofiofi.docxdocx.ui.dialogs.OfficeDialogRouter
import app.ofiofi.docxdocx.ui.dialogs.OfficeModalDialogHost
import app.ofiofi.docxdocx.ui.dialogs.OfficeModalDialogKind
import app.ofiofi.docxdocx.ui.ink.InkEngineBridge
import app.ofiofi.docxdocx.ui.ink.InkStroke
import app.ofiofi.docxdocx.ui.ink.InkToolKind
import app.ofiofi.docxdocx.ui.ink.StylusInkOverlay
import app.ofiofi.docxdocx.ui.ribbon.AdvancedToolPanel
import app.ofiofi.docxdocx.ui.ribbon.FindReplaceOverlayBar
import app.ofiofi.docxdocx.ui.ribbon.QuickNoteComposerBar
import app.ofiofi.docxdocx.ui.ribbon.RibbonAdvancedPresets
import app.ofiofi.docxdocx.ui.shell.AdaptiveShellModel
import app.ofiofi.docxdocx.ui.shell.CommentsSidePane
import app.ofiofi.docxdocx.ui.shell.DesktopEnvironmentDetector
import app.ofiofi.docxdocx.ui.shell.DesktopModePreference
import app.ofiofi.docxdocx.ui.shell.DocCommentItem
import app.ofiofi.docxdocx.ui.shell.DocCommentParser
import app.ofiofi.docxdocx.ui.shell.DocxAdaptiveChrome
import app.ofiofi.docxdocx.ui.shell.DocxFontCatalog
import app.ofiofi.docxdocx.ui.shell.RibbonFormatSummary
import app.ofiofi.docxdocx.ui.shell.ShellMode
import app.ofiofi.docxdocx.ui.theme.OfficeTokens
import uniffi.word_mobile_ffi.CommandOutcome
import uniffi.word_mobile_ffi.MobileDocumentSession
import uniffi.word_mobile_ffi.RulerMetrics
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DocxAssetInitializer.ensureInitialized(applicationContext)
        val initialSession = loadSessionFromIntent(intent) ?: MobileDocumentSession.newBlank("es-CL")
        val initialUri = intent?.data
        val initialTitle = initialUri?.let { resolveDisplayName(it) } ?: "Documento1"
        val catalog = RibbonCatalogLoader.loadFromAssets(applicationContext)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isDark = isSystemInDarkTheme()
            DocxWorkspaceScreen(
                initialSession = initialSession,
                initialTitle = initialTitle,
                initialUri = initialUri,
                catalog = catalog,
                isExpandedWidth = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact,
                isDark = isDark,
            )
        }
    }

    private fun loadSessionFromIntent(intent: Intent?): MobileDocumentSession? {
        val uri = intent?.data ?: return null
        val name = resolveDisplayName(uri) ?: "documento.docx"
        return runCatching {
            val bytes = SafDocumentHelper.readBytes(contentResolver, uri)
            MobileDocumentSession.openBytes(name, bytes, null)
        }.getOrNull()
    }

    private fun resolveDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}

@Composable
fun DocxWorkspaceScreen(
    initialSession: MobileDocumentSession,
    initialTitle: String,
    initialUri: Uri?,
    catalog: RibbonSchemaCatalog,
    isExpandedWidth: Boolean,
    isDark: Boolean,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val surfaces = if (isDark) OfficeTokens.DarkSurfaces else OfficeTokens.LightSurfaces
    val accent = if (isDark) OfficeTokens.AppAccents.Word.accentDark else OfficeTokens.AppAccents.Word.accentLight
    val recentStore = remember(context) { RecentDocumentStore(context) }
    val availableFonts = remember { DocxFontCatalog.loadAllFonts() }

    var inputDeviceGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { inputDeviceGeneration += 1 }
            override fun onInputDeviceRemoved(deviceId: Int) { inputDeviceGeneration += 1 }
            override fun onInputDeviceChanged(deviceId: Int) { inputDeviceGeneration += 1 }
        }
        inputManager?.registerInputDeviceListener(listener, null)
        onDispose { inputManager?.unregisterInputDeviceListener(listener) }
    }

    var desktopPreference by remember { mutableStateOf(DesktopModePreference.AUTO) }
    val envSignals = remember(configuration, inputDeviceGeneration, desktopPreference) {
        DesktopEnvironmentDetector.inspect(
            context = context,
            windowWidthDp = configuration.screenWidthDp,
            preference = desktopPreference,
        )
    }
    val shellMode = remember(envSignals) { DesktopEnvironmentDetector.resolveShellMode(envSignals) }
    val isDesktopOrTablet = isExpandedWidth || shellMode != ShellMode.PHONE
    val formFactor = remember(isDesktopOrTablet) { AdaptiveShellModel.resolveFormFactor(isDesktopOrTablet) }

    var activeSession by remember { mutableStateOf(initialSession) }
    var documentTitle by remember { mutableStateOf(initialTitle) }
    var activeUri by remember { mutableStateOf(initialUri) }
    var recentDocs by remember { mutableStateOf(recentStore.loadRecent()) }
    var docRevision by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var layoutMode by remember { mutableStateOf(DocumentLayoutMode.PRINT_LAYOUT) }
    var selectionGeometry by remember { mutableStateOf(activeSession.getSelectionGeometry()) }
    var viewportMetrics by remember { mutableStateOf(RulerViewportMetrics()) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var activeToggleIds by remember { mutableStateOf(emptySet<String>()) }
    var activeContexts by remember { mutableStateOf(emptySet<String>()) }
    var formatSummary by remember { mutableStateOf(RibbonFormatSummary.Default) }
    var showRuler by remember(shellMode, isDesktopOrTablet) {
        mutableStateOf(shellMode == ShellMode.DESKTOP || isDesktopOrTablet)
    }
    var showCommentsPane by remember { mutableStateOf(false) }
    var activeToolPanel by remember { mutableStateOf<AdvancedToolPanel?>(null) }
    var activeModalDialog by remember { mutableStateOf<OfficeModalDialogKind?>(null) }
    var activeInkTool by remember { mutableStateOf(InkToolKind.NONE) }
    var inkStrokes by remember { mutableStateOf(emptyList<InkStroke>()) }
    var inkReplayTrigger by remember { mutableIntStateOf(0) }
    var statusMessage by remember {
        mutableStateOf("Página 1 de ${activeSession.pageCount()}  •  ${activeSession.wordCount()} palabras")
    }
    var showBackstage by remember { mutableStateOf(false) }

    val currentPageIdx = selectionGeometry.caret?.pageIndex ?: 0u
    val rulerMetrics: RulerMetrics = remember(activeSession, docRevision, currentPageIdx) {
        runCatching { activeSession.getRulerMetrics(currentPageIdx) }
            .getOrElse {
                RulerMetrics(
                    widthPt = 612f,
                    heightPt = 792f,
                    marginTopPt = 72f,
                    marginBottomPt = 72f,
                    marginLeftPt = 72f,
                    marginRightPt = 72f,
                    paraLeftPt = 0f,
                    paraRightPt = 0f,
                    paraFirstLinePt = 0f,
                )
            }
    }
    val commentsList: List<DocCommentItem> = remember(activeSession, docRevision) {
        runCatching {
            val outcome = activeSession.executeCommand("review.comments", "{}")
            DocCommentParser.parseList(outcome.resultJson)
        }.getOrDefault(emptyList())
    }

    val applyOutcome: (CommandOutcome) -> Unit = { outcome ->
        selectionGeometry = outcome.selection
        canUndo = outcome.canUndo
        canRedo = outcome.canRedo
        activeToggleIds = AdaptiveShellModel.parseActiveToggleIds(outcome.formatStateJson)
        activeContexts = AdaptiveShellModel.parseActiveContexts(outcome.formatStateJson)
        formatSummary = AdaptiveShellModel.parseFormatSummary(outcome.formatStateJson)
        docRevision += 1
        val pageIdx = (outcome.selection.caret?.pageIndex?.toInt() ?: 0) + 1
        statusMessage = "Página $pageIdx de ${outcome.pageCount}  •  ${outcome.wordCount} palabras"
    }

    val swapSession: (MobileDocumentSession, String, Uri?, StorageProviderKind, String?) -> Unit =
        { nextSession, title, uri, provider, originStr ->
            activeSession = nextSession
            documentTitle = title
            activeUri = uri
            layoutMode = DocumentLayoutMode.PRINT_LAYOUT
            selectionGeometry = nextSession.getSelectionGeometry()
            canUndo = false
            canRedo = false
            activeToggleIds = emptySet()
            activeContexts = emptySet()
            formatSummary = RibbonFormatSummary.Default
            docRevision += 1
            statusMessage = "Página 1 de ${nextSession.pageCount()}  •  ${nextSession.wordCount()} palabras"
            if (originStr != null) {
                recentStore.record(RecentDocEntry(title, originStr, provider, System.currentTimeMillis()))
                recentDocs = recentStore.loadRecent()
            }
        }

    var inputViewRef by remember { mutableStateOf<WordInputView?>(null) }
    val inputBridge = remember(activeSession) {
        WordInputBridge(SessionDocumentTextSink(activeSession), applyOutcome)
    }

    val dispatchCommand: (String, String) -> Unit = { commandId, paramsJson ->
        val inkTool = InkEngineBridge.toolForCommand(commandId)
        val dialogKind = OfficeDialogRouter.dialogForCommand(commandId)
        val panel = RibbonAdvancedPresets.panelForCommand(commandId)
        when {
            commandId == "ui.openBackstage" -> showBackstage = true
            commandId == "file.print" -> DocxPrintDocumentAdapter.launchPrintJob(context, documentTitle, activeSession)
            commandId == "view.ruler" -> showRuler = !showRuler
            commandId == "view.zoom100" -> zoom = 1.0f
            commandId == "view.comments" || commandId == "review.showComments" -> showCommentsPane = !showCommentsPane
            commandId == "review.newComment" && paramsJson == "{}" -> {
                if (shellMode != ShellMode.PHONE) {
                    showCommentsPane = true
                } else {
                    activeToolPanel = AdvancedToolPanel.NEW_COMMENT
                }
            }
            inkTool != null -> {
                activeInkTool = if (activeInkTool == inkTool && inkTool != InkToolKind.NONE) InkToolKind.NONE else inkTool
            }
            commandId == "draw.replay" -> inkReplayTrigger += 1
            commandId == "draw.inkToShape" -> {
                if (inkStrokes.isNotEmpty()) {
                    inkStrokes = emptyList()
                    runCatching {
                        val outcome = activeSession.executeCommand("insert.shape", """{"kind":"rectangle"}""")
                        applyOutcome(outcome)
                    }
                }
            }
            dialogKind != null -> activeModalDialog = dialogKind
            panel != null && paramsJson == "{}" -> activeToolPanel = panel
            else -> {
                runCatching {
                    val outcome = activeSession.executeCommand(commandId, paramsJson)
                    applyOutcome(outcome)
                }
            }
        }
    }

    val effectiveToggleIds = buildSet {
        addAll(activeToggleIds)
        if (showRuler) add("view.ruler")
        if (showCommentsPane) add("review.showComments")
        when (activeInkTool) {
            InkToolKind.PEN -> add("draw.pen")
            InkToolKind.PENCIL -> add("draw.pencil")
            InkToolKind.HIGHLIGHTER -> add("draw.highlighter")
            InkToolKind.ERASER -> add("draw.eraser")
            InkToolKind.LASSO -> add("draw.lasso")
            InkToolKind.NONE -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                val match = KeyboardShortcutRegistry.resolveKeyEvent(keyEvent.nativeKeyEvent)
                if (match != null) {
                    dispatchCommand(match.commandId, match.paramsJson)
                    true
                } else {
                    false
                }
            },
    ) {
        DocxAdaptiveChrome(
            formFactor = formFactor,
            catalog = catalog,
            documentTitle = documentTitle,
            activeToggleIds = effectiveToggleIds,
            activeContexts = activeContexts,
            formatSummary = formatSummary,
            availableFonts = availableFonts,
            shellMode = shellMode,
            isDexHardware = envSignals.isSamsungDex,
            commentCount = commentsList.count { it.parent == null },
            showCommentsPane = showCommentsPane,
            canUndo = canUndo,
            canRedo = canRedo,
            isDark = isDark,
            onExecuteCommand = dispatchCommand,
            onOpenBackstage = { showBackstage = true },
            onToggleCommentsPane = { showCommentsPane = !showCommentsPane },
            onToggleDesktopMode = {
                desktopPreference = when (shellMode) {
                    ShellMode.DESKTOP -> DesktopModePreference.FORCE_MOBILE
                    ShellMode.TABLET, ShellMode.PHONE -> DesktopModePreference.FORCE_DESKTOP
                }
            },
            canvasContent = { canvasModifier ->
                Column(modifier = canvasModifier) {
                    when (activeToolPanel) {
                        AdvancedToolPanel.FIND_REPLACE -> FindReplaceOverlayBar(
                            surfaces = surfaces,
                            accent = accent,
                            onExecuteCommand = { cmd, json ->
                                runCatching {
                                    val outcome = activeSession.executeCommand(cmd, json)
                                    applyOutcome(outcome)
                                }
                            },
                            onDismiss = { activeToolPanel = null },
                        )
                        AdvancedToolPanel.NEW_COMMENT -> QuickNoteComposerBar(
                            title = "Nuevo comentario",
                            placeholder = "Escribir comentario de revisión...",
                            confirmLabel = "Comentar",
                            surfaces = surfaces,
                            accent = accent,
                            onSubmit = { note ->
                                runCatching {
                                    val outcome = activeSession.executeCommand(
                                        "review.newComment",
                                        RibbonAdvancedPresets.buildCommentPayload(note),
                                    )
                                    applyOutcome(outcome)
                                }
                            },
                            onDismiss = { activeToolPanel = null },
                        )
                        AdvancedToolPanel.NEW_FOOTNOTE -> QuickNoteComposerBar(
                            title = "Nota al pie",
                            placeholder = "Texto de la nota al pie...",
                            confirmLabel = "Insertar nota",
                            surfaces = surfaces,
                            accent = accent,
                            onSubmit = { note ->
                                runCatching {
                                    val outcome = activeSession.executeCommand(
                                        "references.footnote",
                                        RibbonAdvancedPresets.buildFootnotePayload(note),
                                    )
                                    applyOutcome(outcome)
                                }
                            },
                            onDismiss = { activeToolPanel = null },
                        )
                        null -> Unit
                    }
                    if (showRuler) {
                        HorizontalPageRuler(
                            rulerMetrics = rulerMetrics,
                            viewportMetrics = viewportMetrics,
                            zoom = zoom,
                            surfaces = surfaces,
                            accent = accent,
                            onCommitMargins = { leftPt, rightPt ->
                                dispatchCommand("layout.margins", RulerMath.buildMarginsPayload(leftPt = leftPt, rightPt = rightPt))
                            },
                            onCommitIndents = { leftPt, rightPt, firstLinePt ->
                                dispatchCommand("para.indents", RulerMath.buildIndentsPayload(leftPt, rightPt, firstLinePt))
                            },
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (showRuler && isDesktopOrTablet) {
                            VerticalPageRuler(
                                rulerMetrics = rulerMetrics,
                                viewportMetrics = viewportMetrics,
                                surfaces = surfaces,
                                accent = accent,
                                onCommitMargins = { topPt, bottomPt ->
                                    dispatchCommand("layout.margins", RulerMath.buildMarginsPayload(topPt = topPt, bottomPt = bottomPt))
                                },
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            WordInputHost(
                                bridge = inputBridge,
                                onViewReady = { inputViewRef = it },
                                modifier = Modifier.size(1.dp),
                            )
                            DocumentCanvas(
                                session = activeSession,
                                docRevision = docRevision,
                                selectionGeometry = selectionGeometry,
                                isDark = isDark,
                                zoom = zoom,
                                layoutMode = layoutMode,
                                onZoomChange = { zoom = it },
                                onToggleLayoutMode = {
                                    val nextMode = if (layoutMode == DocumentLayoutMode.PRINT_LAYOUT) {
                                        DocumentLayoutMode.MOBILE_REFLOW
                                    } else {
                                        DocumentLayoutMode.PRINT_LAYOUT
                                    }
                                    runCatching {
                                        val cmd = if (nextMode == DocumentLayoutMode.MOBILE_REFLOW) "view.webLayout" else "view.printLayout"
                                        val outcome = activeSession.executeCommand(cmd, "{}")
                                        layoutMode = nextMode
                                        applyOutcome(outcome)
                                    }
                                },
                                onTapPage = { hit, tapCount ->
                                    inputViewRef?.showSoftKeyboard()
                                    runCatching {
                                        selectionGeometry = when (tapCount) {
                                            2 -> activeSession.doubleTapAt(hit.pageIndex.toUInt(), hit.xPt, hit.yPt)
                                            3 -> activeSession.tripleTapAt(hit.pageIndex.toUInt(), hit.xPt, hit.yPt)
                                            else -> activeSession.tapAt(hit.pageIndex.toUInt(), hit.xPt, hit.yPt)
                                        }
                                        val pageIdx = (selectionGeometry.caret?.pageIndex?.toInt() ?: hit.pageIndex) + 1
                                        statusMessage = "Página $pageIdx de ${activeSession.pageCount()}  •  ${activeSession.wordCount()} palabras"
                                    }
                                },
                                onSelectionUpdated = { updated ->
                                    selectionGeometry = updated
                                },
                                onQuickCommand = { cmdId ->
                                    dispatchCommand(cmdId, "{}")
                                },
                                onViewportMetricsChanged = { updatedMetrics ->
                                    viewportMetrics = updatedMetrics
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            StylusInkOverlay(
                                strokes = inkStrokes,
                                activeTool = activeInkTool,
                                activeColorArgb = InkEngineBridge.defaultColorForTool(activeInkTool),
                                replayTrigger = inkReplayTrigger,
                                onAddStroke = { stroke -> inkStrokes = inkStrokes + stroke },
                                onEraseAt = { x, y ->
                                    inkStrokes = inkStrokes.filterNot { InkEngineBridge.hitTestStroke(it, x, y) }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (showCommentsPane) {
                            CommentsSidePane(
                                comments = commentsList,
                                surfaces = surfaces,
                                accent = accent,
                                onExecuteCommand = dispatchCommand,
                                onDismiss = { showCommentsPane = false },
                            )
                        }
                    }
                }
            },
            statusBarContent = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .background(surfaces.statusBar)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(statusMessage, color = surfaces.textDim, fontSize = 11.sp)
                    Text("Español (Chile)  •  ${(zoom * 100f).roundToInt()} %", color = surfaces.textDim, fontSize = 11.sp)
                }
            },
        )

        if (showBackstage) {
            BackstageArchivoOverlay(
                currentTitle = documentTitle,
                activeUri = activeUri,
                recentDocs = recentDocs,
                pageCount = activeSession.pageCount(),
                wordCount = activeSession.wordCount(),
                isDark = isDark,
                isCompactWidth = !isDesktopOrTablet,
                onDismiss = { showBackstage = false },
                onNewFromTemplate = { template ->
                    val next = SafDocumentHelper.createSessionFromTemplate(template, "es-CL")
                    swapSession(next, template.titleEsCl, null, StorageProviderKind.LOCAL_OR_DRIVE_SAF, null)
                },
                onOpenSafUri = { uri ->
                    runCatching {
                        val resolver = context.contentResolver
                        runCatching {
                            resolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            )
                        }
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "documento.docx"
                        val bytes = SafDocumentHelper.readBytes(resolver, uri)
                        val next = MobileDocumentSession.openBytes(name, bytes, null)
                        swapSession(next, name, uri, StorageProviderKind.LOCAL_OR_DRIVE_SAF, uri.toString())
                    }
                },
                onSaveToSafUri = { uri ->
                    runCatching {
                        val bytes = activeSession.saveDocxBytes()
                        SafDocumentHelper.writeBytes(context.contentResolver, uri, bytes)
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "$documentTitle.docx"
                        activeUri = uri
                        documentTitle = name
                        recentStore.record(
                            RecentDocEntry(name, uri.toString(), StorageProviderKind.LOCAL_OR_DRIVE_SAF, System.currentTimeMillis()),
                        )
                        recentDocs = recentStore.loadRecent()
                    }
                },
                onExportPdfToSafUri = { uri ->
                    runCatching {
                        val pdfBytes = activeSession.exportPdfBytes()
                        SafDocumentHelper.writeBytes(context.contentResolver, uri, pdfBytes)
                    }
                },
                onReplaceSessionFromBytes = { filename, bytes, origin, provider ->
                    runCatching {
                        val next = MobileDocumentSession.openBytes(filename, bytes, null)
                        swapSession(next, filename, null, provider, origin)
                    }
                },
                onGetDocxBytes = { activeSession.saveDocxBytes() },
            )
        }

        activeModalDialog?.let { kind ->
            OfficeModalDialogHost(
                dialogKind = kind,
                formatSummary = formatSummary,
                activeToggleIds = activeToggleIds,
                surfaces = surfaces,
                accent = accent,
                onSwitchDialog = { activeModalDialog = it },
                onExecuteCommands = { calls ->
                    calls.forEach { call ->
                        dispatchCommand(call.commandId, call.paramsJson)
                    }
                },
                onDismiss = { activeModalDialog = null },
            )
        }
    }
}
