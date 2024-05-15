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

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.romainguy.kotlin.explorer.build.ByteCodeDecompiler
import dev.romainguy.kotlin.explorer.build.DexCompiler
import dev.romainguy.kotlin.explorer.build.KotlinCompiler
import dev.romainguy.kotlin.explorer.bytecode.ByteCodeParser
import dev.romainguy.kotlin.explorer.code.CodeContent
import dev.romainguy.kotlin.explorer.dex.DexDumpParser
import dev.romainguy.kotlin.explorer.oat.OatDumpParser
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

private const val TotalDisassemblySteps = 7
private const val TotalRunSteps = 2

private val byteCodeDecompiler = ByteCodeDecompiler()
private val byteCodeParser = ByteCodeParser()
private val dexDumpParser = DexDumpParser()
private val oatDumpParser = OatDumpParser()

suspend fun buildAndRun(
    toolPaths: ToolPaths,
    source: String,
    onLogs: (AnnotatedString) -> Unit,
    onStatusUpdate: (String, Float) -> Unit
) = coroutineScope {
    val ui = currentCoroutineContext()

    launch(Dispatchers.IO) {
        val updater = ProgressUpdater(TotalRunSteps, onStatusUpdate)
        try {
            updater.addJob(launch(ui) { updater.update("Compiling Kotlin…") })

            val directory = toolPaths.tempDirectory
            cleanupClasses(directory)

            val path = directory.resolve("KotlinExplorer.kt")
            Files.writeString(path, source)

            val kotlinc = KotlinCompiler(toolPaths, directory).compile(path)

            if (kotlinc.exitCode != 0) {
                withContext(ui) {
                    onLogs(showError(kotlinc.output.replace(path.parent.toString() + "/", "")))
                    updater.advance("Error compiling Kotlin", 2)
                }
                return@launch
            }

            withContext(ui) { updater.advance("Running…") }

            val java = process(
                *buildJavaCommand(toolPaths),
                directory = directory
            )

            withContext(ui) {
                onLogs(showLogs(java.output))
                val status = if (java.exitCode != 0) "Error running code" else "Run completed"
                updater.advance(status)
            }

            updater.addJob(launch(ui) { updater.update("Ready") })
        } finally {
            withContext(ui) { updater.finish() }
        }
    }
}

suspend fun buildAndDisassemble(
    toolPaths: ToolPaths,
    source: String,
    onByteCode: (CodeContent) -> Unit,
    onDex: (CodeContent) -> Unit,
    onOat: (CodeContent) -> Unit,
    onLogs: (AnnotatedString) -> Unit,
    onStatusUpdate: (String, Float) -> Unit,
    optimize: Boolean
) = coroutineScope {
    val ui = currentCoroutineContext()

    launch(Dispatchers.IO) {
        val updater = ProgressUpdater(TotalDisassemblySteps, onStatusUpdate)
        try {
            updater.addJob(launch(ui) { updater.update("Compiling and disassembling…") })

            val directory = toolPaths.tempDirectory
            cleanupClasses(directory)

            val path = directory.resolve("KotlinExplorer.kt")
            Files.writeString(path, source)

            val kotlinc = KotlinCompiler(toolPaths, directory).compile(path)

            if (kotlinc.exitCode != 0) {
                updater.addJob(launch(ui) {
                    onLogs(showError(kotlinc.output.replace(path.parent.toString() + "/", "")))
                    updater.advance("Error compiling Kotlin", updater.steps)
                })
                return@launch
            }
            updater.addJob(launch(ui) { updater.advance("Kotlin compiled") })

            updater.addJob(launch {
                val javap = byteCodeDecompiler.decompile(directory)
                withContext(ui) {
                    val status = if (javap.exitCode != 0) {
                        onLogs(showError(javap.output))
                        "Error Disassembling Java ByteCode"
                    } else {
                        onByteCode(byteCodeParser.parse(javap.output))
                        "Disassembled Java ByteCode"
                    }
                    updater.advance(status)
                }
            })

            val dexCompiler = DexCompiler(toolPaths, directory)

            val dex = dexCompiler.buildDex(optimize)

            if (dex.exitCode != 0) {
                updater.addJob(launch(ui) {
                    onLogs(showError(dex.output))
                    updater.advance("Error creating DEX", 5)
                })
                return@launch
            }
            updater.addJob(launch(ui) {
                updater.advance(if (optimize) "Optimized DEX with R8" else "Compiled DEX with D8")
            })

            updater.addJob(launch {
                val dexdump = dexCompiler.dumpDex()
                withContext(ui) {
                    val status = if (dexdump.exitCode != 0) {
                        onLogs(showError(dexdump.output))
                        "Error creating DEX dump"
                    } else {
                        onDex(dexDumpParser.parse(dexdump.output))
                        "Created DEX dump"
                    }
                    updater.advance(status)
                }
            })

            val push = process(
                toolPaths.adb.toString(),
                "push",
                "classes.dex",
                "/sdcard/classes.dex",
                directory = directory
            )

            if (push.exitCode != 0) {
                updater.addJob(launch(ui) {
                    onLogs(showError(push.output))
                    updater.advance("Error pushing code to device", 3)
                })
                return@launch
            }

            updater.addJob(launch(ui) { updater.advance("Pushed code to device…") })

            val dex2oat = process(
                toolPaths.adb.toString(),
                "shell",
                "dex2oat",
                "--dex-file=/sdcard/classes.dex",
                "--oat-file=/sdcard/classes.oat",
                directory = directory
            )

            if (dex2oat.exitCode != 0) {
                updater.addJob(launch(ui) {
                    onLogs(showError(dex2oat.output))
                    updater.advance("Error compiling OAT", 2)
                })
                return@launch
            }

            updater.addJob(launch(ui) { updater.advance("Disassembling OAT…") })

            val oatdump = process(
                toolPaths.adb.toString(),
                "shell",
                "oatdump",
                "--oat-file=/sdcard/classes.oat",
                directory = directory
            )

            updater.addJob(launch(ui) { onOat(oatDumpParser.parse(oatdump.output)) })

            val status = if (oatdump.exitCode != 0) {
                onLogs(showError(oatdump.output))
                "Error creating oat dump"
            } else {
                "Created oat dump"
            }
            withContext(ui) { updater.advance(status) }

            updater.addJob(launch(ui) { updater.update("Ready") })
        } finally {
            withContext(ui) { updater.finish() }
        }
    }
}

private fun buildJavaCommand(toolPaths: ToolPaths): Array<String> {
    val command = mutableListOf(
        "java",
        "-classpath",
        toolPaths.kotlinLibs.joinToString(":") { jar -> jar.toString() } + ":${toolPaths.platform}" + ":.",
        "KotlinExplorerKt")
    return command.toTypedArray()
}

private fun cleanupClasses(directory: Path) {
    Files
        .list(directory)
        .filter { path -> path.extension == "class" }
        .forEach { path -> path.toFile().delete() }
}

private fun showError(error: String) = buildAnnotatedString {
    withStyle(SpanStyle(ErrorColor)) {
        append(error)
    }
}

private fun showLogs(logs: String) = AnnotatedString(logs)

internal val BuiltInKotlinClass = Regex("^(kotlin|kotlinx|java|javax|org\\.(intellij|jetbrains))\\..+")
