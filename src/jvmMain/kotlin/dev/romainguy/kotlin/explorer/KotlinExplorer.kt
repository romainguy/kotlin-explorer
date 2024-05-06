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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.input.key.Key.Companion.D
import androidx.compose.ui.input.key.Key.Companion.F
import androidx.compose.ui.input.key.Key.Companion.G
import androidx.compose.ui.input.key.Key.Companion.L
import androidx.compose.ui.input.key.Key.Companion.O
import androidx.compose.ui.input.key.Key.Companion.P
import androidx.compose.ui.text.style.TextAlign.Companion.Center
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.compose.ui.window.WindowPosition.Aligned
import dev.romainguy.kotlin.explorer.Shortcut.Ctrl
import dev.romainguy.kotlin.explorer.Shortcut.CtrlShift
import dev.romainguy.kotlin.explorer.dex.DexTextArea
import dev.romainguy.kotlin.explorer.oat.OatTextArea
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
    var status by remember { mutableStateOf("Ready") }
    var progress by remember { mutableStateOf(1f) }

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
            activeTextArea = e?.component as RSyntaxTextArea
        }

        override fun focusLost(e: FocusEvent?) {
        }
    }}

    val sourceTextArea = remember { sourceTextArea(focusTracker, explorerState) }
    val dexTextArea = remember { dexTextArea(explorerState, focusTracker) }
    val oatTextArea = remember { oatTextArea(explorerState, focusTracker) }

    val findDialog = remember { FindDialog(window, searchListener).apply { searchContext.searchWrap = true } }
    var showSettings by remember { mutableStateOf(!explorerState.toolPaths.isValid) }

    val sourcePanel: @Composable () -> Unit = { SourcePanel(sourceTextArea, explorerState) }
    val dexPanel: @Composable () -> Unit = { TextPanel("DEX", dexTextArea, explorerState) }
    val oatPanel: @Composable () -> Unit = { TextPanel("OAT", oatTextArea, explorerState) }
    var panels by remember { mutableStateOf(explorerState.getPanels(sourcePanel, dexPanel, oatPanel)) }
    val onProgressUpdate: (String, Float) -> Unit = { newStatus: String, newProgress: Float ->
        status = newStatus
        progress = newProgress
    }
    
    MainMenu(
        explorerState,
        sourceTextArea,
        { dex ->
            if (dex != null) {
                updateTextArea(dexTextArea, dex)
            } else {
                dexTextArea.refreshText()
            }
        },
        { oat -> updateTextArea(oatTextArea, oat) },
        onProgressUpdate,
        { findDialog.isVisible = true },
        { SearchEngine.find(activeTextArea, findDialog.searchContext) },
        { showSettings = true },
        { panels = explorerState.getPanels(sourcePanel, dexPanel, oatPanel) },
    )

    if (showSettings) {
        Settings(
            explorerState,
            onSaveClick = { showSettings = !explorerState.toolPaths.isValid }
        )
    } else {
        Column(
            modifier = Modifier.background(JewelTheme.globalColors.paneBackground)
        ) {
            MultiSplitter(modifier = Modifier.weight(1.0f), panels)
            Row(verticalAlignment = CenterVertically) {
                val width = 160.dp
                Text(
                    modifier = Modifier
                        .widthIn(min = width, max = width)
                        .padding(8.dp),
                    text = status
                )
                if (progress < 1) {
                    LinearProgressIndicator({ progress })
                }
            }
        }
    }
}

private fun ExplorerState.getPanels(
    sourcePanel: @Composable () -> Unit,
    dexPanel: @Composable () -> Unit,
    oatPanel: @Composable () -> Unit,
): List<@Composable () -> Unit> {
    return buildList {
        add(sourcePanel)
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
private fun TextPanel(title: String, textArea: RSyntaxTextArea, explorerState: ExplorerState) {
    Column {
        Title(title)
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = { RTextScrollPane(textArea) },
            update = { textArea.updateStyle(explorerState) })
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
        configureSyntaxTextArea(SyntaxConstants.SYNTAX_STYLE_KOTLIN)
        addFocusListener(focusTracker)
        SwingUtilities.invokeLater { requestFocusInWindow() }
        document.addDocumentListener(DocumentChangeListener { explorerState.sourceCode = text })
    }
}

private fun dexTextArea(explorerState: ExplorerState, focusTracker: FocusListener): DexTextArea {
    return DexTextArea(explorerState).apply {
        configureSyntaxTextArea(SyntaxConstants.SYNTAX_STYLE_NONE)
        addFocusListener(focusTracker)
    }
}

private fun oatTextArea(explorerState: ExplorerState, focusTracker: FocusListener): RSyntaxTextArea {
    return OatTextArea(explorerState).apply {
        configureSyntaxTextArea(SyntaxConstants.SYNTAX_STYLE_NONE)
        addFocusListener(focusTracker)
    }
}

@Composable
private fun FrameWindowScope.MainMenu(
    explorerState: ExplorerState,
    sourceTextArea: RSyntaxTextArea,
    onDexUpdate: (String?) -> Unit,
    onOatUpdate: (String) -> Unit,
    onStatusUpdate: (String, Float) -> Unit,
    onFindClicked: () -> Unit,
    onFindNextClicked: () -> Unit,
    onOpenSettings: () -> Unit,
    onPanelsUpdated: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val compileAndDisassemble: () -> Unit = {
        scope.launch {
            disassemble(
                explorerState.toolPaths,
                sourceTextArea.text,
                onDexUpdate,
                onOatUpdate,
                onStatusUpdate,
                explorerState.optimize
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
            MenuCheckboxItem("Show DEX", Ctrl(D), explorerState::showDex, onShowPanelChanged)
            MenuCheckboxItem("Show OAT", Ctrl(O), explorerState::showOat, onShowPanelChanged)
            MenuCheckboxItem("Show Line Numbers", CtrlShift(L), explorerState::showLineNumbers) {
                onDexUpdate(null)
            }
            Separator()
            MenuCheckboxItem("Presentation Mode", CtrlShift(P), explorerState::presentationMode)
        }
        Menu("Compilation") {
            MenuCheckboxItem("Optimize with R8", CtrlShift(O), explorerState::optimize)
            MenuItem("Compile & Disassemble", CtrlShift(D), onClick = compileAndDisassemble)
        }
    }
}

private fun RSyntaxTextArea.configureSyntaxTextArea(syntaxStyle: String) {
    syntaxEditingStyle = syntaxStyle
    isCodeFoldingEnabled = true
    antiAliasingEnabled = true
    tabsEmulated = true
    tabSize = 4
    applyTheme(this)
    currentLineHighlightColor = java.awt.Color.decode("#F5F8FF")
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

private fun updateTextArea(textArea: RSyntaxTextArea, text: String) {
    val position = textArea.caretPosition
    textArea.text = text
    textArea.caretPosition = minOf(position, textArea.document.length)
}

fun main() = application {
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
