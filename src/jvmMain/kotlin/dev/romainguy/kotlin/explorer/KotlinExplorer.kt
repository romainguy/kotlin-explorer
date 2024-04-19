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

@file:OptIn(ExperimentalSplitPaneApi::class)
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
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
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
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

@Composable
private fun FrameWindowScope.KotlinExplorer(
    explorerState: ExplorerState
) {
    var sourceTextArea by remember { mutableStateOf<RSyntaxTextArea?>(null) }
    var dexTextArea by remember { mutableStateOf<RSyntaxTextArea?>(null) }
    var oatTextArea by remember { mutableStateOf<RSyntaxTextArea?>(null) }
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
    val findDialog = remember { FindDialog(window, searchListener).apply { searchContext.searchWrap = true } }
    var showSettings by remember { mutableStateOf(!explorerState.toolPaths.isValid) }

    MainMenu(
        explorerState,
        sourceTextArea,
        { dex -> dexTextArea!!.text = dex },
        { oat -> oatTextArea!!.text = oat },
        { statusUpdate -> status = statusUpdate },
        { findDialog.isVisible = true },
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
            HorizontalSplitPane(
                modifier = Modifier.weight(1.0f),
                splitPaneState = rememberSplitPaneState(initialPositionPercentage = 0.3f)
            ) {
                first {
                    SwingPanel(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            sourceTextArea = RSyntaxTextArea().apply {
                                configureSyntaxTextArea(SyntaxConstants.SYNTAX_STYLE_KOTLIN)
                                addFocusListener(focusTracker)
                                SwingUtilities.invokeLater { requestFocusInWindow() }
                                document.addDocumentListener(object : DocumentListener {
                                    override fun insertUpdate(e: DocumentEvent?) {
                                        explorerState.sourceCode = text
                                    }

                                    override fun removeUpdate(e: DocumentEvent?) {
                                        explorerState.sourceCode = text
                                    }

                                    override fun changedUpdate(e: DocumentEvent?) {
                                        explorerState.sourceCode = text
                                    }
                                })
                            }
                            RTextScrollPane(sourceTextArea)
                        },
                        update = {
                            sourceTextArea?.text = explorerState.sourceCode
                        }
                    )
                }
                second {
                    HorizontalSplitPane(
                        modifier = Modifier.weight(1.0f),
                        splitPaneState = rememberSplitPaneState(initialPositionPercentage = 0.5f)
                    ) {
                        first {
                            SwingPanel(
                                modifier = Modifier.fillMaxSize(),
                                factory = {
                                    dexTextArea = DexTextArea().apply {
                                        configureSyntaxTextArea(SyntaxConstants.SYNTAX_STYLE_NONE)
                                        addFocusListener(focusTracker)
                                    }
                                    RTextScrollPane(dexTextArea)
                                }
                            )
                        }
                        second {
                            SwingPanel(
                                modifier = Modifier.fillMaxSize(),
                                factory = {
                                    oatTextArea = RSyntaxTextArea().apply {
                                        configureSyntaxTextArea(SyntaxConstants.SYNTAX_STYLE_NONE)
                                        addFocusListener(focusTracker)
                                    }
                                    RTextScrollPane(oatTextArea)
                                }
                            )
                        }
                        splitter {
                            HorizontalSplitter()
                        }
                    }
                }
                splitter {
                    HorizontalSplitter()
                }
            }
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
private fun FrameWindowScope.MainMenu(
    explorerState: ExplorerState,
    sourceTextArea: RSyntaxTextArea?,
    onDexUpdate: (String) -> Unit,
    onOatUpdate: (String) -> Unit,
    onStatusUpdate: (String) -> Unit,
    onSearchClicked: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()

    MenuBar {
        Menu("File") {
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
            Item(
                "Settings…",
                onClick = onOpenSettings
            )
        }
        Menu("Edit") {
            Item(
                "Search",
                shortcut = KeyShortcut(
                    key = Key.F,
                    ctrl = !isMac,
                    meta = isMac,
                ),
                onClick = onSearchClicked
            )
        }
        Menu("Options") {
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
