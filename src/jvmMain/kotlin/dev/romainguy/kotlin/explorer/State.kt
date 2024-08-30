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

package dev.romainguy.kotlin.explorer

import androidx.compose.runtime.*
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPlacement.Floating
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readLines

private const val AndroidHome = "ANDROID_HOME"
private const val KotlinHome = "KOTLIN_HOME"
private const val Optimize = "OPTIMIZE"
private const val KeepEverything = "KEEP_EVERYTHING"
private const val R8Rules = "R8_RULES"
private const val AutoBuildOnStartup = "AUTO_BUILD_ON_STARTUP"
private const val Presentation = "PRESENTATION"
private const val ShowLineNumbers = "SHOW_LINE_NUMBERS"
private const val ShowByteCode = "SHOW_BYTE_CODE"
private const val ShowDex = "SHOW_DEX"
private const val ShowOat = "SHOW_OAT"
private const val SyncLines = "SYNC_LINES"
private const val Indent = "INDENT"
private const val DecompileHiddenIsa = "DECOMPILE_HIDDEN_ISA"
private const val LineNumberWidth = "LINE_NUMBER_WIDTH"
private const val WindowPosX = "WINDOW_X"
private const val WindowPosY = "WINDOW_Y"
private const val WindowWidth = "WINDOW_WIDTH"
private const val WindowHeight = "WINDOW_HEIGHT"
private const val Placement = "WINDOW_PLACEMENT"

@Stable
class ExplorerState {
    val directory: Path = settingsPath()
    private val file: Path = directory.resolve("settings")
    private val entries: MutableMap<String, String> = readSettings(file)

    var androidHome by StringState(AndroidHome, System.getenv("ANDROID_HOME") ?: System.getProperty("user.home"))
    var kotlinHome by StringState(KotlinHome, System.getenv("KOTLIN_HOME") ?: System.getProperty("user.home"))
    var toolPaths by mutableStateOf(createToolPaths())
    var optimize by BooleanState(Optimize, true)
    var keepEverything by BooleanState(KeepEverything, true)
    var r8Rules by StringState(R8Rules, "")
    var autoBuildOnStartup by BooleanState(AutoBuildOnStartup, false)
    var presentationMode by BooleanState(Presentation, false)
    var showLineNumbers by BooleanState(ShowLineNumbers, false)
    var showByteCode by BooleanState(ShowByteCode, false)
    var showDex by BooleanState(ShowDex, true)
    var showOat by BooleanState(ShowOat, true)
    var showLogs by mutableStateOf(false)
    var syncLines by BooleanState(SyncLines, true)
    var lineNumberWidth by IntState(LineNumberWidth, 4)
    var indent by IntState(Indent, 4)
    var decompileHiddenIsa by BooleanState(DecompileHiddenIsa, true)
    var sourceCode: String = readSourceCode(toolPaths)
    var windowWidth by IntState(WindowWidth, 1900)
    var windowHeight by IntState(WindowHeight, 1600)
    var windowPosX by IntState(WindowPosX, -1)
    var windowPosY by IntState(WindowPosY, -1)
    var windowPlacement by SettingsState(Placement, Floating) { WindowPlacement.valueOf(this) }

    fun reloadToolPathsFromSettings() {
        toolPaths = createToolPaths()
    }

    private fun createToolPaths() =
        ToolPaths(directory, Path.of(androidHome.toString()), Path.of(kotlinHome.toString()))

    private inner class BooleanState(key: String, initialValue: Boolean) :
        SettingsState<Boolean>(key, initialValue, { toBoolean() })

    private inner class IntState(key: String, initialValue: Int) :
        SettingsState<Int>(key, initialValue, { toInt() })

    private inner class StringState(key: String, initialValue: String) :
        SettingsState<String>(key, initialValue, { this })

    private open inner class SettingsState<T>(private val key: String, initialValue: T, parse: String.() -> T) :
        MutableState<T> {
        private val state = mutableStateOf(entries[key]?.parse() ?: initialValue)
        override var value: T
            get() = state.value
            set(value) {
                entries[key] = value.toString()
                state.value = value
            }

        override fun component1() = state.component1()

        override fun component2() = state.component2()
    }

    fun writeSourceCodeState() {
        Files.writeString(toolPaths.sourceFile, sourceCode)
    }

    fun writeState() {
        writeSourceCodeState()
        Files.writeString(
            file,
            entries.map { (key, value) -> "$key=${value.replace("\n", "\\\n")}" }.joinToString("\n")
        )
    }
}

private fun settingsPath() = Paths.get(System.getProperty("user.home"), ".kotlin-explorer").apply {
    if (!exists()) Files.createDirectory(this)
}

private fun readSettings(file: Path): MutableMap<String, String> {
    val settings = mutableMapOf<String, String>()
    if (!file.exists()) return settings

    val lines = file.readLines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val index = line.indexOf('=')
        if (index != -1) {
            var value = line.substring(index + 1)
            if (value.endsWith('\\')) {
                value = value.dropLast(1) + '\n'
                do {
                    i++
                    if (i >= lines.size) break
                    value += lines[i].dropLast(1) + '\n'
                } while (lines[i].endsWith('\\'))
            }
            settings[line.substring(0, index)] = value
        }
        i++
    }

    return settings
}

private fun readSourceCode(toolPaths: ToolPaths) = if (toolPaths.sourceFile.exists()) {
    Files.readString(toolPaths.sourceFile)
} else {
    """
    // NOTE: If Build > Keep Everything is *not* checked, used the @Keep
    // annotation to keep the classes/methods/etc. you want to disassemble
    fun square(a: Int): Int {
        return a * a
    }

    """.trimIndent()
}
