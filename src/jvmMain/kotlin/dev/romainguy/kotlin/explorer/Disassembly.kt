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
import dev.romainguy.kotlin.explorer.code.ISA
import dev.romainguy.kotlin.explorer.dex.DexDumpParser
import dev.romainguy.kotlin.explorer.oat.OatDumpParser
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory

private const val TotalDisassemblySteps = 7
private const val TotalRunSteps = 2

private val byteCodeDecompiler = ByteCodeDecompiler()
private val byteCodeParser = ByteCodeParser()
private val dexDumpParser = DexDumpParser()
private val oatDumpParser = OatDumpParser()

suspend fun buildAndRun(
    toolPaths: ToolPaths,
    kotlinOnlyConsumers: Boolean,
    compilerFlags: String,
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
            writeSupportFiles(directory)

            val kotlinc = KotlinCompiler(toolPaths, directory).compile(kotlinOnlyConsumers, compilerFlags, path)

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
    kotlinOnlyConsumers: Boolean,
    compilerFlags: String,
    r8rules: String,
    minApi: Int,
    instructionSets: Map<ISA, Boolean>,
    onByteCode: (CodeContent) -> Unit,
    onDex: (CodeContent) -> Unit,
    onOat: (CodeContent) -> Unit,
    onLogs: (AnnotatedString) -> Unit,
    onStatusUpdate: (String, Float) -> Unit,
    optimize: Boolean,
    keepEverything: Boolean
) = coroutineScope {
    val ui = currentCoroutineContext()

    onLogs(showLogs(""))

    launch(Dispatchers.IO) {
        val updater = ProgressUpdater(TotalDisassemblySteps, onStatusUpdate)
        try {
            updater.addJob(launch(ui) { updater.update("Compiling and disassembling…") })

            val directory = toolPaths.tempDirectory
            cleanupClasses(directory)

            val path = directory.resolve("KotlinExplorer.kt")
            Files.writeString(path, source)
            writeSupportFiles(directory)

            val kotlinc = KotlinCompiler(toolPaths, directory).compile(kotlinOnlyConsumers, compilerFlags, path)

            if (kotlinc.exitCode != 0) {
                updater.addJob(launch(ui) {
                    onLogs(showError(kotlinc.output.replace(path.parent.toString() + "/", "")))
                    updater.advance("Error compiling Kotlin", updater.steps)
                })
                return@launch
            }
            updater.addJob(launch(ui) { updater.advance("Kotlin compiled") })

            if (instructionSets.getOrDefault(ISA.ByteCode, true)) {
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
            } else {
                launch(ui) { updater.advance("") }
            }

            val dexCompiler = DexCompiler(toolPaths, directory, r8rules, minApi)

            val dex = dexCompiler.buildDex(optimize, keepEverything)

            if (dex.exitCode != 0) {
                updater.addJob(launch(ui) {
                    onLogs(showError(dex.output))
                    updater.advance("Error creating DEX", updater.steps)
                })
                return@launch
            }
            updater.addJob(launch(ui) {
                updater.advance(if (optimize) "Optimized DEX with R8" else "Compiled DEX with D8")
            })

            if (instructionSets.getOrDefault(ISA.Dex, true)) {
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
            } else {
                launch(ui) { updater.advance("") }
            }

            if (!instructionSets.getOrDefault(ISA.Oat, true)) {
                updater.waitForJobs()
                withContext(ui) {
                    updater.skipToEnd("Ready")
                }
                return@launch
            }

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
                    updater.advance("Error pushing code to device", updater.steps)
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
                    updater.advance("Error compiling OAT", updater.steps)
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
        .forEach { path ->
            if (path.extension == "class") {
                path.toFile().delete()
            } else if (path.isDirectory()) {
                path.toFile().deleteRecursively()
            }
        }
}

private fun writeSupportFiles(directory: Path) {
    Files.writeString(
        directory.resolve("NeverInline.kt"),
        """
@file:OptIn(ExperimentalMultiplatform::class)

package dalvik.annotation.optimization

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
public annotation class NeverInline()
        """.trimIndent()
    )

    Files.writeString(
        directory.resolve("Keep.kt"),
        """
import java.lang.annotation.ElementType.ANNOTATION_TYPE
import java.lang.annotation.ElementType.CONSTRUCTOR
import java.lang.annotation.ElementType.FIELD
import java.lang.annotation.ElementType.METHOD
import java.lang.annotation.ElementType.PACKAGE
import java.lang.annotation.ElementType.TYPE

@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FILE,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FIELD
)
@Suppress("DEPRECATED_JAVA_ANNOTATION", "SupportAnnotationUsage")
@java.lang.annotation.Target(PACKAGE, TYPE, ANNOTATION_TYPE, CONSTRUCTOR, METHOD, FIELD)
public annotation class Keep 
        """.trimIndent()
    )
}

private fun showError(error: String) = buildAnnotatedString {
    withStyle(SpanStyle(ErrorColor)) {
        append(error)
    }
}

private fun showLogs(logs: String) = AnnotatedString(logs)

internal val BuiltInKotlinClass = Regex("^(kotlin|kotlinx|java|javax|org\\.(intellij|jetbrains))\\..+")
