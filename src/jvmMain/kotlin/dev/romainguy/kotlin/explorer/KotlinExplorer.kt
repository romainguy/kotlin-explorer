/*
 * Copyright (C) 2023 Romain Guy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("FunctionName")

package dev.romainguy.kotlin.explorer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.Key.Companion.A
import androidx.compose.ui.input.key.Key.Companion.B
import androidx.compose.ui.input.key.Key.Companion.C
import androidx.compose.ui.input.key.Key.Companion.Comma
import androidx.compose.ui.input.key.Key.Companion.D
import androidx.compose.ui.input.key.Key.Companion.F
import androidx.compose.ui.input.key.Key.Companion.G
import androidx.compose.ui.input.key.Key.Companion.L
import androidx.compose.ui.input.key.Key.Companion.O
import androidx.compose.ui.input.key.Key.Companion.P
import androidx.compose.ui.input.key.Key.Companion.R
import androidx.compose.ui.input.key.Key.Companion.S
import androidx.compose.ui.input.key.Key.Companion.V
import androidx.compose.ui.input.key.Key.Companion.X
import androidx.compose.ui.input.key.Key.Companion.Z
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign.Companion.Center
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.compose.ui.window.WindowPosition.Aligned
import dev.romainguy.kotlin.explorer.Shortcut.*
import dev.romainguy.kotlin.explorer.code.CodeContent
import dev.romainguy.kotlin.explorer.code.CodeStyle
import dev.romainguy.kotlin.explorer.code.CodeTextArea
import dev.romainguy.kotlin.explorer.code.SyntaxStyle
import kotlinx.coroutines.launch
import org.fife.rsta.ui.search.FindDialog
import org.fife.rsta.ui.search.SearchEvent
import org.fife.rsta.ui.search.SearchListener
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.Theme
import org.fife.ui.rtextarea.RTextArea
import org.fife.ui.rtextarea.RTextScrollPane
import org.fife.ui.rtextarea.SearchEngine
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import org.jetbrains.jewel.window.styling.TitleBarStyle
import java.awt.Desktop
import java.awt.event.ActionEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.io.IOException
import javax.swing.FocusManager
import javax.swing.SwingUtilities
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

private const val FontSizeEditingMode = 12.0f
private const val FontSizePresentationMode = 20.0f

private class UiState(val explorerState: ExplorerState, window: ComposeWindow) {
    var activeTextArea by mutableStateOf<RSyntaxTextArea?>(null)
    var previousActiveTextArea by mutableStateOf<RSyntaxTextArea?>(null)
    var status by mutableStateOf("Ready")
    var progress by mutableStateOf(1f)
    var logs by mutableStateOf(AnnotatedString(""))

    val searchListener = object : SearchListener {
        override fun searchEvent(e: SearchEvent?) {
            when (e?.type) {
                SearchEvent.Type.MARK_ALL -> {
                    val result = SearchEngine.markAll(activeTextArea, e.searchContext)
                    if (!result.wasFound()) {
                        status = "Not found"
                    }
                }
                SearchEvent.Type.FIND -> {
                    val result = SearchEngine.find(activeTextArea, e.searchContext)
                    if (!result.wasFound()) {
                        status = "Not found"
                    }
                }
                SearchEvent.Type.REPLACE -> {
                }
                SearchEvent.Type.REPLACE_ALL -> {
                }
                null -> {
                }
            }
        }

        override fun getSelectedText(): String {
            return ""
        }
    }
    val focusTracker = object : FocusListener {
        override fun focusGained(e: FocusEvent?) {
            previousActiveTextArea = activeTextArea
            activeTextArea = e?.component as RSyntaxTextArea
        }

        override fun focusLost(e: FocusEvent?) {
            // TODO: Bit of a hack to keep focus on the text areas. Without this, clicking
            //       the background loses focus, so does opening/closing panels
            if (e?.oppositeComponent !is RSyntaxTextArea) {
                if (activeTextArea?.isShowing == true) {
                    activeTextArea?.requestFocusInWindow()
                } else {
                    previousActiveTextArea?.requestFocusInWindow()
                }
            }
        }
    }

    val sourceTextArea: SourceTextArea = sourceTextArea(focusTracker, explorerState).apply { requestFocusInWindow() }
    val byteCodeTextArea = byteCodeTextArea(explorerState, focusTracker, sourceTextArea)
    val dexTextArea = dexTextArea(explorerState, focusTracker, sourceTextArea)
    val oatTextArea = oatTextArea(explorerState, focusTracker)

    val codeTextAreas = listOf(byteCodeTextArea, dexTextArea, oatTextArea)

    val findDialog = FindDialog(window, searchListener).apply { searchContext.searchWrap = true }
    var showSettings by mutableStateOf(!explorerState.toolPaths.isValid)

    val updatePresentationMode: (Boolean) -> Unit = {
        listOf(byteCodeTextArea, dexTextArea, oatTextArea).forEach { area -> area.presentationMode = it }
    }
    val updateSyncLinesEnabled: (Boolean) -> Unit = {
        listOf(byteCodeTextArea, dexTextArea).forEach { area -> area.isSyncLinesEnabled = it }
        sourceTextArea.isSyncLinesEnabled = it
    }

    val updateShowLineNumbers: (Boolean) -> Unit = {
        listOf(byteCodeTextArea, dexTextArea).forEach { area ->
            area.codeStyle = area.codeStyle.withShowLineNumbers(it)
        }
    }

    val onProgressUpdate: (String, Float) -> Unit = { newStatus: String, newProgress: Float ->
        status = newStatus
        progress = newProgress
    }

    val onLogsUpdate: (AnnotatedString) -> Unit = { text ->
        logs = text
        if (text.isNotEmpty()) {
            explorerState.showLogs = true
        }
    }
}

@Composable
private fun FrameWindowScope.KotlinExplorer(
    explorerState: ExplorerState
) {
    val uiState = remember { UiState(explorerState, window) }

    uiState.sourceTextArea.addCodeTextAreas(uiState.byteCodeTextArea, uiState.dexTextArea)

    val sourcePanel: @Composable () -> Unit =
        { SourcePanel(uiState.sourceTextArea, explorerState) }
    val byteCodePanel: @Composable () -> Unit =
        { TextPanel(uiState.byteCodeTextArea, explorerState) }
    val dexPanel: @Composable () -> Unit =
        { TextPanel(uiState.dexTextArea, explorerState) }
    val oatPanel: @Composable () -> Unit =
        { TextPanel(uiState.oatTextArea, explorerState) }
    var panels by remember { mutableStateOf(explorerState.getPanels(sourcePanel, byteCodePanel, dexPanel, oatPanel)) }

    MainMenu(
        explorerState,
        uiState.sourceTextArea,
        uiState.byteCodeTextArea::setContent,
        uiState.dexTextArea::setContent,
        uiState.oatTextArea::setContent,
        uiState.onLogsUpdate,
        uiState.onProgressUpdate,
        { uiState.findDialog.isVisible = true },
        { SearchEngine.find(uiState.activeTextArea, uiState.findDialog.searchContext) },
        { uiState.showSettings = true },
        { panels = explorerState.getPanels(sourcePanel, byteCodePanel, dexPanel, oatPanel) },
        uiState.updateShowLineNumbers,
        uiState.updatePresentationMode,
        uiState.updateSyncLinesEnabled,
    )

    if (isMac) {
        Desktop.getDesktop().setPreferencesHandler {
            uiState.showSettings = true
        }
    }

    if (uiState.showSettings) {
        SettingsDialog(explorerState, uiState)
    }

    Column(modifier = Modifier.background(JewelTheme.globalColors.panelBackground)) {
        VerticalOptionalPanel(
            modifier = Modifier.weight(1.0f),
            showOptionalPanel = explorerState.showLogs,
            optionalPanel = { LogsPanel(uiState.logs) }
        ) {
            MultiSplitter(modifier = Modifier.weight(1.0f), panels)
        }
        StatusBar(uiState.status, uiState.progress)
    }
}

@Composable
private fun SettingsDialog(
    explorerState: ExplorerState,
    uiState: UiState
) {
    val onDismissRequest = { uiState.showSettings = false }
    val onSaveRequest = {
        uiState.showSettings = false
        uiState.codeTextAreas.forEach {
            it.codeStyle = it.codeStyle.withSettings(explorerState.indent, explorerState.lineNumberWidth)
        }
    }
    DialogWindow(
        onCloseRequest = onDismissRequest,
        state = rememberDialogState(size = DpSize.Unspecified),
        visible = uiState.showSettings,
        title = "Settings",
        resizable = false,
        onPreviewKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape) {
                onDismissRequest()
                true
            } else {
                false
            }
        }
    ) {
        Settings(explorerState, onSaveRequest = onSaveRequest, onDismissRequest = onDismissRequest)
    }
}

@Composable
private fun LogsPanel(logs: AnnotatedString) {
    Column {
        Title("Logs")
        SelectionContainer {
            Text(
                text = logs,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
                    .border(1.dp, JewelTheme.globalColors.borders.normal)
                    .padding(8.dp)
                    .focusable(false)
            )
        }
    }
}

@Composable
private fun StatusBar(status: String, progress: Float) {
    Row(verticalAlignment = CenterVertically) {
        val width = 220.dp
        Text(
            modifier = Modifier
                .widthIn(min = width, max = width)
                .padding(8.dp),
            text = status
        )
        if (progress < 1) {
            LinearProgressIndicator(
                progress = { progress },
                color = ProgressColor,
                trackColor = ProgressTrackColor,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

private fun ExplorerState.getPanels(
    sourcePanel: @Composable () -> Unit,
    byteCodePanel: @Composable () -> Unit,
    dexPanel: @Composable () -> Unit,
    oatPanel: @Composable () -> Unit,
): List<@Composable () -> Unit> {
    return buildList {
        add(sourcePanel)
        if (showByteCode) {
            add(byteCodePanel)
        }
        if (showDex) {
            add(dexPanel)
        }
        if (showOat) {
            add(oatPanel)
        }
    }
}

@Composable
private fun SourcePanel(sourceTextArea: RSyntaxTextArea, explorerState: ExplorerState) {
    Column {
        Title("Source")
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                RTextScrollPane(sourceTextArea)
            },
            update = {
                if (explorerState.sourceCode != sourceTextArea.text) {
                    sourceTextArea.text = explorerState.sourceCode
                }
                sourceTextArea.updateStyle(explorerState)
            }
        )
    }
}

@Composable
private fun TextPanel(textArea: RSyntaxTextArea, explorerState: ExplorerState) {
    SwingPanel(
        modifier = Modifier.fillMaxSize(),
        factory = { RTextScrollPane(textArea) },
        update = { textArea.updateStyle(explorerState) }
    )
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        textAlign = Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

private fun sourceTextArea(focusTracker: FocusListener, explorerState: ExplorerState): SourceTextArea {
    return SourceTextArea(explorerState.syncLines).apply {
        configureSyntaxTextArea(SyntaxConstants.SYNTAX_STYLE_KOTLIN, focusTracker)
        SwingUtilities.invokeLater { requestFocusInWindow() }
        document.addDocumentListener(DocumentChangeListener { explorerState.sourceCode = text })
    }
}

private fun byteCodeTextArea(state: ExplorerState, focusTracker: FocusListener, sourceTextArea: SourceTextArea) =
    codeTextArea(state, focusTracker, syntaxStyle = SyntaxStyle.ByteCode, sourceTextArea = sourceTextArea)

private fun dexTextArea(state: ExplorerState, focusTracker: FocusListener, sourceTextArea: SourceTextArea) =
    codeTextArea(state, focusTracker, syntaxStyle = SyntaxStyle.Dex, sourceTextArea = sourceTextArea)

private fun oatTextArea(state: ExplorerState, focusTracker: FocusListener) =
    codeTextArea(state, focusTracker, hasLineNumbers = false, syntaxStyle = SyntaxStyle.Oat)

private fun codeTextArea(
    state: ExplorerState,
    focusTracker: FocusListener,
    hasLineNumbers: Boolean = true,
    syntaxStyle: String = SyntaxConstants.SYNTAX_STYLE_NONE,
    sourceTextArea: SourceTextArea? = null,
): CodeTextArea {
    val codeStyle = CodeStyle(state.indent, state.showLineNumbers && hasLineNumbers, state.lineNumberWidth)
    return CodeTextArea(state.presentationMode, codeStyle, state.syncLines, sourceTextArea).apply {
        configureSyntaxTextArea(syntaxStyle, focusTracker)
    }
}

@Composable
private fun FrameWindowScope.MainMenu(
    explorerState: ExplorerState,
    sourceTextArea: RSyntaxTextArea,
    onByteCodeUpdate: (CodeContent) -> Unit,
    onDexUpdate: (CodeContent) -> Unit,
    onOatUpdate: (CodeContent) -> Unit,
    onLogsUpdate: (AnnotatedString) -> Unit,
    onStatusUpdate: (String, Float) -> Unit,
    onFindClicked: () -> Unit,
    onFindNextClicked: () -> Unit,
    onOpenSettings: () -> Unit,
    onPanelsUpdated: () -> Unit,
    onShowLineNumberChanged: (Boolean) -> Unit,
    onPresentationModeChanged: (Boolean) -> Unit,
    onSyncLinesChanged: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val compileAndDisassemble: () -> Unit = {
        scope.launch {
            buildAndDisassemble(
                explorerState.toolPaths,
                sourceTextArea.text,
                onByteCodeUpdate,
                onDexUpdate,
                onOatUpdate,
                onLogsUpdate,
                onStatusUpdate,
                explorerState.optimize
            )
        }
    }
    val compileAndRun: () -> Unit = {
        scope.launch {
            buildAndRun(
                explorerState.toolPaths,
                sourceTextArea.text,
                onLogsUpdate,
                onStatusUpdate
            )
        }
    }

    var firstRun by remember { mutableStateOf(true) }
    if (firstRun) {
        firstRun = false
        if (explorerState.autoBuildOnStartup) {
            compileAndDisassemble()
        }
    }

    MenuBar {
        if (!isMac) {
            Menu("File") {
                MenuItem("Settings…", CtrlAlt(S), onClick = onOpenSettings)
            }
        }
        Menu("Edit") {
            MenuItem("Undo", Ctrl(Z), onClick = { performSwingMenuAction(RTextArea.UNDO_ACTION) })
            MenuItem("Redo", CtrlShift(Z), onClick = { performSwingMenuAction(RTextArea.REDO_ACTION) })
            Separator()
            MenuItem("Cut", Ctrl(X), onClick = { performSwingMenuAction(RTextArea.CUT_ACTION) })
            MenuItem("Copy", Ctrl(C), onClick = { performSwingMenuAction(RTextArea.COPY_ACTION) })
            MenuItem("Paste", Ctrl(V), onClick = { performSwingMenuAction(RTextArea.PASTE_ACTION) })
            Separator()
            MenuItem("Select All", Ctrl(A), onClick = { performSwingMenuAction(RTextArea.SELECT_ALL_ACTION) })
            Separator()
            MenuItem("Find…", Ctrl(F), onClick = onFindClicked)
            MenuItem("Find Next Occurrence", Ctrl(G), onClick = onFindNextClicked)
        }
        Menu("View") {
            val onShowPanelChanged: (Boolean) -> Unit = { onPanelsUpdated() }
            MenuCheckboxItem("Show Byte Code", Ctrl(B), explorerState::showByteCode, onShowPanelChanged)
            MenuCheckboxItem("Show DEX", Ctrl(D), explorerState::showDex, onShowPanelChanged)
            MenuCheckboxItem("Show OAT", Ctrl(O), explorerState::showOat, onShowPanelChanged)
            MenuCheckboxItem("Show Line Numbers", CtrlShift(L), explorerState::showLineNumbers) {
                onShowLineNumberChanged(it)
            }
            Separator()
            MenuCheckboxItem("Sync Lines", Ctrl(S), explorerState::syncLines, onSyncLinesChanged)
            Separator()
            MenuCheckboxItem("Show Logs", Ctrl(L), explorerState::showLogs)
            Separator()
            MenuCheckboxItem("Presentation Mode", CtrlShift(P), explorerState::presentationMode) {
                onPresentationModeChanged(it)
            }
        }
        Menu("Build") {
            MenuItem(
                "Run",
                CtrlOnly(R),
                onClick = compileAndRun,
                enabled = explorerState.toolPaths.isValid
            )
            Separator()
            MenuCheckboxItem("Optimize with R8", CtrlShift(O), explorerState::optimize)
            MenuCheckboxItem("Build on Startup", shortcut = null, explorerState::autoBuildOnStartup)
            MenuItem(
                "Build & Disassemble",
                CtrlShift(D),
                onClick = compileAndDisassemble,
                enabled = explorerState.toolPaths.isValid
            )
        }
    }
}

private fun performSwingMenuAction(actionType: Int) {
    RTextArea.getAction(actionType).actionPerformed(
        ActionEvent(
            FocusManager.getCurrentManager().focusOwner, 0, ""
        )
    )
}

private fun RSyntaxTextArea.configureSyntaxTextArea(syntaxStyle: String, focusTracker: FocusListener) {
    syntaxEditingStyle = syntaxStyle
    isCodeFoldingEnabled = true
    antiAliasingEnabled = true
    tabsEmulated = true
    tabSize = 4
    applyTheme(this, syntaxStyle)
    addFocusListener(focusTracker)
}

private fun applyTheme(textArea: RSyntaxTextArea, syntaxStyle: String) {
    try {
        val theme = Theme.load(UiState::class.java.getResourceAsStream(when (syntaxStyle) {
            SyntaxStyle.ByteCode -> "/themes/kotlin_explorer_disassembly.xml"
            SyntaxStyle.Dex -> "/themes/kotlin_explorer_disassembly.xml"
            SyntaxStyle.Oat -> "/themes/kotlin_explorer_disassembly.xml"
            else -> "/themes/kotlin_explorer.xml"
        }))
        theme.apply(textArea)
    } catch (ioe: IOException) {
        ioe.printStackTrace()
    }
}

private fun RSyntaxTextArea.updateStyle(explorerState: ExplorerState) {
    val presentation = explorerState.presentationMode
    font = font.deriveFont(if (presentation) FontSizePresentationMode else FontSizeEditingMode)
}

@OptIn(ExperimentalJewelApi::class)
fun main() {
    System.setProperty("apple.awt.application.name", "Kotlin Explorer")

    application {
        // Faster scrolling in Swing components
        enableNewSwingCompositing()

        val explorerState = remember { ExplorerState() }

        val windowState = rememberWindowState(
            size = explorerState.getWindowSize(),
            position = explorerState.getWindowPosition(),
            placement = explorerState.windowPlacement,
        )

        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown(explorerState, windowState)
        })

        val themeDefinition = if (KotlinExplorerTheme.System.isDark()) {
            JewelTheme.darkThemeDefinition()
        } else {
            JewelTheme.lightThemeDefinition()
        }
        val titleBarStyle = if (KotlinExplorerTheme.System.isDark()) {
            TitleBarStyle.dark()
        } else {
            TitleBarStyle.light()
        }

        IntUiTheme(
            themeDefinition,
            ComponentStyling.decoratedWindow(titleBarStyle = titleBarStyle),
            false
        ) {
            DecoratedWindow(
                state = windowState,
                onCloseRequest = {
                    explorerState.setWindowState(windowState)
                    exitApplication()
                },
                title = "Kotlin Explorer"
            ) {
                TitleBar(Modifier.newFullscreenControls()) {
                    Text("Kotlin Explorer")
                }
                KotlinExplorer(explorerState)
            }
        }
    }
}

@OptIn(ExperimentalPathApi::class)
private fun shutdown(
    explorerState: ExplorerState,
    windowState: WindowState
) {
    explorerState.setWindowState(windowState)
    explorerState.writeState()
    explorerState.toolPaths.tempDirectory.deleteRecursively()
}

private fun ExplorerState.getWindowSize() = DpSize(windowWidth.dp, windowHeight.dp)

private fun ExplorerState.getWindowPosition(): WindowPosition {
    val x = windowPosX
    val y = windowPosY
    return if (x > 0 && y > 0) WindowPosition(x.dp, y.dp) else Aligned(Alignment.Center)
}

private fun ExplorerState.setWindowState(windowState: WindowState) {
    windowWidth = windowState.size.width.value.toInt()
    windowHeight = windowState.size.height.value.toInt()
    windowPosX = windowState.position.x.value.toInt()
    windowPosY = windowState.position.y.value.toInt()
    windowPlacement = windowState.placement
}

private fun CodeStyle.withSettings(indent: Int, lineNumberWidth: Int) =
    copy(indent = indent, lineNumberWidth = lineNumberWidth)

private fun CodeStyle.withShowLineNumbers(value: Boolean) = copy(showLineNumbers = value)
