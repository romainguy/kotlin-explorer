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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key.Companion.B
import androidx.compose.ui.input.key.Key.Companion.D
import androidx.compose.ui.input.key.Key.Companion.F
import androidx.compose.ui.input.key.Key.Companion.G
import androidx.compose.ui.input.key.Key.Companion.L
import androidx.compose.ui.input.key.Key.Companion.O
import androidx.compose.ui.input.key.Key.Companion.P
import androidx.compose.ui.input.key.Key.Companion.R
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
import kotlinx.coroutines.launch
import org.fife.rsta.ui.search.FindDialog
import org.fife.rsta.ui.search.SearchEvent
import org.fife.rsta.ui.search.SearchListener
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.Theme
import org.fife.ui.rtextarea.RTextScrollPane
import org.fife.ui.rtextarea.SearchEngine
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
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.io.IOException
import javax.swing.SwingUtilities

private const val FontSizeEditingMode = 12.0f
private const val FontSizePresentationMode = 20.0f

@Composable
private fun FrameWindowScope.KotlinExplorer(
    explorerState: ExplorerState
) {
    // TODO: Move all those remembers to an internal private state object
    var activeTextArea by remember { mutableStateOf<RSyntaxTextArea?>(null) }
    var previousActiveTextArea by remember { mutableStateOf<RSyntaxTextArea?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var progress by remember { mutableStateOf(1f) }
    var logs by remember { mutableStateOf("") }

    val searchListener = remember { object : SearchListener {
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
    }}
    val focusTracker = remember { object : FocusListener {
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
    }}

    val sourceTextArea = remember { sourceTextArea(focusTracker, explorerState).apply { requestFocusInWindow() } }
    val byteCodeTextArea = remember { byteCodeTextArea(explorerState, focusTracker) }
    val dexTextArea = remember { dexTextArea(explorerState, focusTracker) }
    val oatTextArea = remember { oatTextArea(explorerState, focusTracker) }
    val codeTextAreas = listOf(byteCodeTextArea, dexTextArea, oatTextArea)

    val findDialog = remember { FindDialog(window, searchListener).apply { searchContext.searchWrap = true } }
    var showSettings by remember { DialogState(!explorerState.toolPaths.isValid) }

    val sourcePanel: @Composable () -> Unit = { SourcePanel(sourceTextArea, explorerState, showSettings) }
    val byteCodePanel: @Composable () -> Unit = { TextPanel("Byte Code", byteCodeTextArea, explorerState, showSettings) }
    val dexPanel: @Composable () -> Unit = { TextPanel("DEX", dexTextArea, explorerState, showSettings) }
    val oatPanel: @Composable () -> Unit = { TextPanel("OAT", oatTextArea, explorerState, showSettings) }
    var panels by remember { mutableStateOf(explorerState.getPanels(sourcePanel, byteCodePanel, dexPanel, oatPanel)) }

    val updatePresentationMode: (Boolean) -> Unit = {
        listOf(dexTextArea, oatTextArea).forEach { area -> area.presentationMode = it }
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
    val onLogsUpdate: (String) -> Unit = { text ->
        logs = text
        if (text.isNotEmpty()) {
            explorerState.showLogs = true
        }
    }

    MainMenu(
        explorerState,
        sourceTextArea,
        byteCodeTextArea::setContent,
        dexTextArea::setContent,
        oatTextArea::setContent,
        onLogsUpdate,
        onProgressUpdate,
        { findDialog.isVisible = true },
        { SearchEngine.find(activeTextArea, findDialog.searchContext) },
        { showSettings = true },
        { panels = explorerState.getPanels(sourcePanel, byteCodePanel, dexPanel, oatPanel) },
        updateShowLineNumbers,
        updatePresentationMode,
    )

    if (showSettings) {
        Settings(explorerState, onDismissRequest = {
            showSettings = false
            codeTextAreas.forEach {
                it.codeStyle = it.codeStyle.withSettings(explorerState.indent, explorerState.lineNumberWidth)
            }
        })
    }

    Column(modifier = Modifier.background(JewelTheme.globalColors.paneBackground)) {
        VerticalOptionalPanel(
            modifier = Modifier.weight(1.0f),
            showOptionalPanel = explorerState.showLogs,
            optionalPanel = { LogsPanel(logs) }
        ) {
            MultiSplitter(modifier = Modifier.weight(1.0f), panels = panels)
        }
        StatusBar(status, progress)
    }
}

@Composable
private fun LogsPanel(logs: String) {
    Column {
        Title("Logs")
        SelectionContainer {
            Text(
                text = logs,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .weight(1.0f)
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
        val width = 190.dp
        Text(
            modifier = Modifier
                .widthIn(min = width, max = width)
                .padding(8.dp),
            text = status
        )
        if (progress < 1) {
            LinearProgressIndicator(
                progress = { progress },
                color = Color(0xff3369d6),
                trackColor = Color(0xffc4c4c4),
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
private fun SourcePanel(sourceTextArea: RSyntaxTextArea, explorerState: ExplorerState, showSettings: Boolean) {
    Column {
        Title("Source")
        DialogSupportingSwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                RTextScrollPane(sourceTextArea)
            },
            update = {
                if (explorerState.sourceCode != sourceTextArea.text) {
                    sourceTextArea.text = explorerState.sourceCode
                }
                sourceTextArea.updateStyle(explorerState)
            },
            isDialogVisible = showSettings,
        )
    }
}

@Composable
private fun TextPanel(title: String, textArea: RSyntaxTextArea, explorerState: ExplorerState, showSettings: Boolean) {
    Column {
        Title(title)
        DialogSupportingSwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = { RTextScrollPane(textArea) },
            update = { textArea.updateStyle(explorerState) },
            isDialogVisible = showSettings
        )
    }
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

private fun sourceTextArea(focusTracker: FocusListener, explorerState: ExplorerState): RSyntaxTextArea {
    return RSyntaxTextArea().apply {
        configureSyntaxTextArea(SyntaxConstants.SYNTAX_STYLE_KOTLIN, focusTracker)
        SwingUtilities.invokeLater { requestFocusInWindow() }
        document.addDocumentListener(DocumentChangeListener { explorerState.sourceCode = text })
    }
}

private fun byteCodeTextArea(state: ExplorerState, focusTracker: FocusListener) =
    codeTextArea(state, focusTracker)

private fun dexTextArea(state: ExplorerState, focusTracker: FocusListener) =
    codeTextArea(state, focusTracker)

private fun oatTextArea(state: ExplorerState, focusTracker: FocusListener) =
    codeTextArea(state, focusTracker, hasLineNumbers = false)

private fun codeTextArea(
    state: ExplorerState,
    focusTracker: FocusListener,
    hasLineNumbers: Boolean = true
): CodeTextArea {
    val codeStyle = CodeStyle(state.indent, state.showLineNumbers && hasLineNumbers, state.lineNumberWidth)
    return CodeTextArea(state.presentationMode, codeStyle).apply {
        configureSyntaxTextArea(SyntaxConstants.SYNTAX_STYLE_NONE, focusTracker)
    }
}

@Composable
private fun FrameWindowScope.MainMenu(
    explorerState: ExplorerState,
    sourceTextArea: RSyntaxTextArea,
    onByteCodeUpdate: (CodeContent) -> Unit,
    onDexUpdate: (CodeContent) -> Unit,
    onOatUpdate: (CodeContent) -> Unit,
    onLogsUpdate: (String) -> Unit,
    onStatusUpdate: (String, Float) -> Unit,
    onFindClicked: () -> Unit,
    onFindNextClicked: () -> Unit,
    onOpenSettings: () -> Unit,
    onPanelsUpdated: () -> Unit,
    onShowLineNumberChanged: (Boolean) -> Unit,
    onPresentationModeChanged: (Boolean) -> Unit,
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

    MenuBar {
        Menu("File") {
            Item("Settings…", onClick = onOpenSettings)
        }
        Menu("Edit") {
            MenuItem("Find…", Ctrl(F), onClick = onFindClicked)
            MenuItem("Find Next Occurrence", Ctrl(G), onClick = onFindNextClicked)
        }
        Menu("View") {
            val onShowPanelChanged: (Boolean) -> Unit = { onPanelsUpdated() }
            MenuCheckboxItem("Show ByteCode", Ctrl(B), explorerState::showByteCode, onShowPanelChanged)
            MenuCheckboxItem("Show DEX", Ctrl(D), explorerState::showDex, onShowPanelChanged)
            MenuCheckboxItem("Show OAT", Ctrl(O), explorerState::showOat, onShowPanelChanged)
            MenuCheckboxItem("Show Line Numbers", CtrlShift(L), explorerState::showLineNumbers) {
                onShowLineNumberChanged(it)
            }
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
            MenuItem(
                "Build & Disassemble",
                CtrlShift(D),
                onClick = compileAndDisassemble,
                enabled = explorerState.toolPaths.isValid
            )
        }
    }
}

private fun RSyntaxTextArea.configureSyntaxTextArea(syntaxStyle: String, focusTracker: FocusListener) {
    syntaxEditingStyle = syntaxStyle
    isCodeFoldingEnabled = true
    antiAliasingEnabled = true
    tabsEmulated = true
    tabSize = 4
    applyTheme(this)
    currentLineHighlightColor = java.awt.Color.decode("#F5F8FF")
    addFocusListener(focusTracker)
}

private fun applyTheme(textArea: RSyntaxTextArea) {
    try {
        val theme = Theme.load(RSyntaxTextArea::class.java.getResourceAsStream(
            "/org/fife/ui/rsyntaxtextarea/themes/idea.xml")
        )
        theme.apply(textArea)
    } catch (ioe: IOException) {
        ioe.printStackTrace()
    }
}

private fun RSyntaxTextArea.updateStyle(explorerState: ExplorerState) {
    val presentation = explorerState.presentationMode
    font = font.deriveFont(if (presentation) FontSizePresentationMode else FontSizeEditingMode)
}

fun main() = application {
    // TODO: Needed to properly composite Compose on top of Swing
    // System.setProperty("compose.interop.blending", "true")

    val explorerState = remember { ExplorerState() }

    Runtime.getRuntime().addShutdownHook(Thread { explorerState.writeState() })

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
        val windowState = rememberWindowState(
            size = explorerState.getWindowSize(),
            position = explorerState.getWindowPosition(),
            placement = explorerState.windowPlacement,
        )
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