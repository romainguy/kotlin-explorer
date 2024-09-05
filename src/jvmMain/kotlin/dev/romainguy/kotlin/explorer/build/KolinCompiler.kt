/*
 * Copyright (C) 2024 Romain Guy
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

package dev.romainguy.kotlin.explorer.build

import dev.romainguy.kotlin.explorer.ToolPaths
import dev.romainguy.kotlin.explorer.process
import java.nio.file.Path

class KotlinCompiler(private val toolPaths: ToolPaths, private val outputDirectory: Path) {
    suspend fun compile(compilerFlags: String, source: Path) =
        process(*buildCompileCommand(compilerFlags, source), directory = outputDirectory)

    private fun buildCompileCommand(compilerFlags: String, file: Path): Array<String> {
        val command = mutableListOf(
            toolPaths.kotlinc.toString(),
            "-Xmulti-platform",
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-classpath",
            (toolPaths.kotlinLibs + listOf(toolPaths.platform)).joinToString(":") { jar -> jar.toString() },
            file.toString(),
            file.parent.resolve("Keep.kt").toString()
        ).apply {
            // TODO: Do something smarter in case a flag looks like -foo="something with space"
            addAll(compilerFlags.split(' '))
        }

        return command.toTypedArray()
    }
}
