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

import dev.romainguy.kotlin.explorer.DependencyCache
import dev.romainguy.kotlin.explorer.ProcessResult
import dev.romainguy.kotlin.explorer.ToolPaths
import dev.romainguy.kotlin.explorer.process
import java.io.File
import java.nio.file.Path
import kotlin.io.path.useLines

private val BuiltInFiles = listOf(
    "Keep.kt",
    "NeverInline.kt"
)

class KotlinCompiler(
    private val toolPaths: ToolPaths,
    settingsDirectory: Path,
    private val outputDirectory: Path,
) {
    private val dependencyCache = DependencyCache(settingsDirectory.resolve("dependency-cache"))

    suspend fun compile(
        kotlinOnlyConsumers: Boolean,
        compilerFlags: String,
        composeVersion: String,
        source: Path
    ): ProcessResult {
        val sb = StringBuilder()
        val result = process(
            *buildCompileCommand(
                kotlinOnlyConsumers,
                compilerFlags,
                composeVersion,
                source
            ) { sb.appendLine(it) }, directory = outputDirectory
        )
        return ProcessResult(result.exitCode, "$sb\n${result.output}")
    }

    private suspend fun buildCompileCommand(
        kotlinOnlyConsumers: Boolean,
        compilerFlags: String,
        composeVersion: String,
        file: Path,
        onOutput: (String) -> Unit
    ): Array<String> {
        val isCompose = file.isCompose()
        val classpath = buildList {
            addAll(toolPaths.kotlinLibs)
            add(toolPaths.platform)
            if (isCompose) {
                add(
                    dependencyCache.getDependency(
                        "androidx.compose.runtime",
                        "runtime-android",
                        composeVersion,
                        onOutput,
                    )
                )
            }
        }.joinToString((File.pathSeparator)) { it.toString() }

        val command = buildList {
            add(toolPaths.kotlinc.toString())
            add("-Xmulti-platform")
            add("-classpath")
            add(classpath)
            if (kotlinOnlyConsumers) {
                this += "-Xno-param-assertions"
                this += "-Xno-call-assertions"
                this += "-Xno-receiver-assertions"
            }
            if (compilerFlags.isNotEmpty() || compilerFlags.isNotBlank()) {
                // TODO: Do something smarter in case a flag looks like -foo="something with space"
                addAll(compilerFlags.split(' '))
            }
            if (isCompose) {
                val composePlugin = dependencyCache.getDependency(
                    "org.jetbrains.kotlin",
                    "kotlin-compose-compiler-plugin",
                    getKotlinVersion(),
                    onOutput,
                )
                add("-Xplugin=$composePlugin")
            }
            // Source code to compile
            this += file.toString()
            for (fileName in BuiltInFiles) {
                this += file.parent.resolve(fileName).toString()
            }
        }

        return command.toTypedArray()
    }

    private suspend fun getKotlinVersion(): String {
        val command = mutableListOf(
            toolPaths.kotlinc.toString(),
            "-version",
        ).toTypedArray()
        return process(*command).output.substringAfter("kotlinc-jvm ").substringBefore(" ")
    }
}

private fun Path.isCompose(): Boolean {
    return useLines { lines ->
        lines.filter { it.trim().startsWith("import") }
            .any { it.split(" ").last() == "androidx.compose.runtime.Composable" }
    }
}