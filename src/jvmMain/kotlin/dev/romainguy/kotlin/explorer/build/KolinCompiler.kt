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
import dev.romainguy.kotlin.explorer.isWindows
import dev.romainguy.kotlin.explorer.process
import java.nio.file.Path

private val BuiltInFiles = listOf(
    "Keep.kt",
    "NeverInline.kt"
)

class KotlinCompiler(private val toolPaths: ToolPaths, private val outputDirectory: Path) {
    suspend fun compile(kotlinOnlyConsumers: Boolean, compilerFlags: String, source: Path) =
        process(*buildCompileCommand(kotlinOnlyConsumers, compilerFlags, source), directory = outputDirectory)

    private fun buildCompileCommand(kotlinOnlyConsumers: Boolean, compilerFlags: String, file: Path): Array<String> {
        val command = mutableListOf(
            toolPaths.kotlinc.toString(),
            "-Xmulti-platform",
            "-classpath",
            (toolPaths.kotlinLibs + listOf(toolPaths.platform)).joinToString(if (isWindows) ";" else ":") { jar -> jar.toString() }
        ).apply {
            if (kotlinOnlyConsumers) {
                this += "-Xno-param-assertions"
                this += "-Xno-call-assertions"
                this += "-Xno-receiver-assertions"
            }
            if (compilerFlags.isNotEmpty() || compilerFlags.isNotBlank()) {
                // TODO: Do something smarter in case a flag looks like -foo="something with space"
                addAll(compilerFlags.split(' '))
            }
            // Source code to compile
            this += file.toString()
            for (fileName in BuiltInFiles) {
                this += file.parent.resolve(fileName).toString()
            }
        }

        return command.toTypedArray()
    }
}
