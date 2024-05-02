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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readLines

private const val Optimize = "OPTIMIZE"
private const val Presentation = "PRESENTATION"
private const val ShowLineNumbers = "SHOW_LINE_NUMBERS"

@Stable
class ExplorerState(
    val settings: Settings = Settings()
) {
    var toolPaths by mutableStateOf(createToolPaths(settings))
    var optimize by mutableStateOf(settings.getValue(Optimize, "true").toBoolean())
    var presentationMode by mutableStateOf(settings.getValue(Presentation, "false").toBoolean())
    var showLineNumbers by mutableStateOf(settings.getValue(ShowLineNumbers, "true").toBoolean())
    var sourceCode: String = readSourceCode(toolPaths)

    fun reloadToolPathsFromSettings() {
        toolPaths = createToolPaths(settings)
    }
}

data class Settings(
    val directory: Path = settingsPath(),
    val file: Path = directory.resolve("settings"),
    val entries: MutableMap<String, String> = readSettings(file)
) {
    fun getValue(name: String, defaultValue: String) = entries[name] ?: defaultValue
}

private fun settingsPath() = Paths.get(System.getProperty("user.home"), ".kotlin-explorer").apply {
    if (!exists()) Files.createDirectory(this)
}

private fun readSettings(file: Path): MutableMap<String, String> {
    val settings = mutableMapOf<String, String>()
    if (!file.exists()) return settings

    val delimiter = Regex.fromLiteral("=")
    file.readLines().forEach { line ->
        val parts = line.split(delimiter, 2)
        if (parts.size == 2) {
            settings[parts[0]] = parts[1]
        }
    }
    return settings
}

private fun readSourceCode(toolPaths: ToolPaths) = if (toolPaths.sourceFile.exists()) {
    Files.readString(toolPaths.sourceFile)
} else {
    "fun square(a: Int): Int {\n    return a * a\n}\n"
}

fun writeState(state: ExplorerState) {
    Files.writeString(
        state.toolPaths.sourceFile,
        state.sourceCode
    )
    state.settings.entries[Optimize] = state.optimize.toString()
    state.settings.entries[Presentation] = state.presentationMode.toString()
    state.settings.entries[ShowLineNumbers] = state.showLineNumbers.toString()

    Files.writeString(
        state.settings.file,
        state.settings.entries
            .map { "${it.key}=${it.value}" }
            .joinToString("\n")
    )
}

