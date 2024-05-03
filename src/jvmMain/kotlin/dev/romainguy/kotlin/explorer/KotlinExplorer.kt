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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
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
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
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
    val oatTextArea = remember { oatTextArea(focusTracker) }

    val findDialog = remember { FindDialog(window, searchListener).apply { searchContext.searchWrap = true } }
    var showSettings by remember { mutableStateOf(!explorerState.toolPaths.isValid) }

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
        { statusUpdate -> status = statusUpdate },
        { findDialog.isVisible = true },
        { SearchEngine.find(activeTextArea, findDialog.searchContext) },
        { showSettings = true }
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
            MultiSplitter(
                modifier = Modifier.weight(1.0f),
                { SourcePanel(sourceTextArea, explorerState) },
                { TextPanel(dexTextArea, explorerState) },
                { TextPanel(oatTextArea, explorerState) },
            )
            Row {
                Text(
                    modifier = Modifier
                        .weight(1.0f, true)
                        .align(Alignment.CenterVertically)
                        .padding(8.dp),
                    text = status
                )
            }
        }
    }
}

@Composable
private fun SourcePanel(sourceTextArea: RSyntaxTextArea, explorerState: ExplorerState) {
    SwingPanel(
        modifier = Modifier.fillMaxSize(),
        factory = {
            RTextScrollPane(sourceTextArea)
        },
        update = {
            sourceTextArea.text = explorerState.sourceCode
            sourceTextArea.setFont(explorerState)
        }
    )
}

@Composable
private fun TextPanel(textArea: RSyntaxTextArea, explorerState: ExplorerState) {
    SwingPanel(
        modifier = Modifier.fillMaxSize(),
        factory = { RTextScrollPane(textArea) },
        update = { textArea.setFont(explorerState) })
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

private fun oatTextArea(focusTracker: FocusListener): RSyntaxTextArea {
    return RSyntaxTextArea().apply {
        configureSyntaxTextArea(SyntaxConstants.SYNTAX_STYLE_NONE)
        addFocusListener(focusTracker)
    }
}

@Composable
private fun FrameWindowScope.MainMenu(
    explorerState: ExplorerState,
    sourceTextArea: RSyntaxTextArea?,
    onDexUpdate: (String?) -> Unit,
    onOatUpdate: (String) -> Unit,
    onStatusUpdate: (String) -> Unit,
    onFindClicked: () -> Unit,
    onFindNextClicked: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()

    MenuBar {
        Menu("File") {
            Item(
                "Settings…",
                onClick = onOpenSettings
            )
        }
        Menu("Edit") {
            Item(
                "Find…",
                shortcut = KeyShortcut(
                    key = Key.F,
                    ctrl = !isMac,
                    meta = isMac,
                ),
                onClick = onFindClicked
            )
            Item(
                "Find Next Occurrence",
                shortcut = KeyShortcut(
                    key = Key.G,
                    ctrl = !isMac,
                    meta = isMac,
                ),
                onClick = onFindNextClicked
            )

        }
        Menu("View") {
            CheckboxItem(
                "Presentation Mode",
                explorerState.presentationMode,
                shortcut = KeyShortcut(
                    key = Key.P,
                    ctrl = !isMac,
                    shift = true,
                    meta = isMac
                ),
                onCheckedChange = { explorerState.presentationMode = it }
            )
            CheckboxItem(
                "Show Line Numbers",
                explorerState.showLineNumbers,
                shortcut = KeyShortcut(
                    key = Key.L,
                    ctrl = !isMac,
                    shift = true,
                    meta = isMac
                ),
                onCheckedChange = {
                    explorerState.showLineNumbers = it
                    onDexUpdate(null)
                }
            )
        }
        Menu("Compilation") {
            CheckboxItem(
                "Optimize with R8",
                explorerState.optimize,
                shortcut = KeyShortcut(
                    key = Key.O,
                    ctrl = !isMac,
                    shift = true,
                    meta = isMac
                ),
                onCheckedChange = { explorerState.optimize = it }
            )
            Item(
                "Compile & Disassemble",
                shortcut = KeyShortcut(
                    key = Key.D,
                    ctrl = !isMac,
                    shift = true,
                    meta = isMac,
                ),
                onClick = {
                    scope.launch {
                        disassemble(
                            explorerState.toolPaths,
                            sourceTextArea!!.text,
                            onDexUpdate,
                            onOatUpdate,
                            onStatusUpdate,
                            explorerState.optimize
                        )
                    }
                }
            )
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

@Composable
private fun ErrorIcon() {
    Icon(
        "icons/error.svg",
        iconClass = Settings::class.java,
        contentDescription = "Error",
        tint = Color(0xffee4056)
    )
}

@Composable
private fun ValidIcon() {
    Icon(
        "icons/done.svg",
        iconClass = Settings::class.java,
        contentDescription = "Valid",
        tint = Color(0xff3369d6)
    )
}


@Composable
private fun Settings(
    explorerState: ExplorerState,
    onSaveClick: () -> Unit
) {
    var androidHome by remember { mutableStateOf(explorerState.toolPaths.androidHome.toString()) }
    var kotlinHome by remember { mutableStateOf(explorerState.toolPaths.kotlinHome.toString()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.align(Alignment.Center)) {
            Row {
                Text(
                    "Android home directory: ",
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                TextField(
                    androidHome,
                    { text -> androidHome = text },
                    modifier = Modifier.defaultMinSize(minWidth = 360.dp),
                    trailingIcon = {
                        if (!explorerState.toolPaths.isAndroidHomeValid) {
                            ErrorIcon()
                        } else {
                            ValidIcon()
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    "Kotlin home directory: ",
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                TextField(
                    kotlinHome,
                    { text -> kotlinHome = text },
                    modifier = Modifier.defaultMinSize(minWidth = 360.dp),
                    trailingIcon = {
                        if (!explorerState.toolPaths.isKotlinHomeValid) {
                            ErrorIcon()
                        } else {
                            ValidIcon()
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            DefaultButton(
                {
                    explorerState.settings.entries["ANDROID_HOME"] = androidHome
                    explorerState.settings.entries["KOTLIN_HOME"] = kotlinHome
                    explorerState.reloadToolPathsFromSettings()
                    onSaveClick()
                }
            ) {
                Text("Save")
            }
        }
    }
}

private fun RSyntaxTextArea.setFont(explorerState: ExplorerState) {
    val presentation = explorerState.presentationMode
    font = font.deriveFont(if (presentation) FontSizePresentationMode else FontSizeEditingMode)
}

fun main() = application {
    val explorerState = remember { ExplorerState() }

    Runtime.getRuntime().addShutdownHook(Thread { writeState(explorerState) })

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
            state = rememberWindowState(
                position = WindowPosition.Aligned(Alignment.Center),
                width = 1900.dp,
                height = 1600.dp
            ),
            onCloseRequest = ::exitApplication,
            title = "Kotlin Explorer"
        ) {
            TitleBar(Modifier.newFullscreenControls()) {
                Text("Kotlin Explorer")
            }
            KotlinExplorer(explorerState)
        }
    }
}
