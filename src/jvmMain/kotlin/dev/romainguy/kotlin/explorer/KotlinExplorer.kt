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

package dev.romainguy.kotlin.explorer

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import org.fife.rsta.ui.search.FindDialog
import org.fife.rsta.ui.search.FindToolBar
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
import java.awt.Color
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.io.IOException
import java.nio.file.Files
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.io.path.exists

@Stable
class ExplorerState(
    val toolPaths: ToolPaths = ToolPaths()
) {
    var optimize by mutableStateOf(true)
    var sourceCode = "fun square(a: Int): Int {\n    return a * a\n}\n"

    init {
        // TODO: Don't do this on the main thread
        if (toolPaths.sourceFile.exists()) {
            sourceCode = Files.readString(toolPaths.sourceFile)
        }
    }
}

@Composable
@Preview
fun FrameWindowScope.KotlinExplorer(
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
    val findDialog = remember { FindDialog(window, searchListener) }

    MainMenu(
        explorerState,
        sourceTextArea,
        { dex -> dexTextArea!!.text = dex },
        { oat -> oatTextArea!!.text = oat },
        { statusUpdate -> status = statusUpdate },
        { findDialog.isVisible = true }
    )

    MaterialTheme {
        Column {
            Row {
                SwingPanel(
                    modifier = Modifier.weight(1.0f, true).height(32.dp),
                    factory = {
                        FindToolBar(searchListener).apply {
                            searchContext = findDialog.searchContext.apply {
                                searchWrap = true
                            }
                        }
                    }
                )
            }
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
                                    dexTextArea = RSyntaxTextArea().apply {
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
    onSearchClicked: () -> Unit
) {
    val scope = rememberCoroutineScope()

    MenuBar {
        Menu("File") {
            Item(
                "Decompile",
                shortcut = KeyShortcut(Key.D, shift = true, meta = true),
                onClick = {
                    scope.disassemble(
                        explorerState.toolPaths,
                        sourceTextArea!!.text,
                        onDexUpdate,
                        onOatUpdate,
                        onStatusUpdate,
                        explorerState.optimize
                    )
                }
            )
        }
        Menu("Edit") {
            Item(
                "Search",
                shortcut = KeyShortcut(Key.F, meta = true),
                onClick = onSearchClicked
            )
        }
        Menu("Options") {
            CheckboxItem(
                "Optimize with R8",
                explorerState.optimize,
                shortcut = KeyShortcut(Key.O, shift = true, meta = true),
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
    applyTheme(this)
    currentLineHighlightColor = Color.decode("#F5F8FF")
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

fun main() = application {
    val explorerState = remember() { ExplorerState() }

    Runtime.getRuntime().addShutdownHook(Thread {
        Files.writeString(explorerState.toolPaths.sourceFile, explorerState.sourceCode)
    })

    Window(
        state = rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            width = 1900.dp,
            height = 1600.dp
        ),
        onCloseRequest = ::exitApplication,
        title = "Kotlin Explorer"
    ) {
        KotlinExplorer(explorerState)
    }
}
