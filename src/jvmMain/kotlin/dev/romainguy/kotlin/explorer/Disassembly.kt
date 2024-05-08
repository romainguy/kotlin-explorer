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

import dev.romainguy.kotlin.explorer.build.ByteCodeDecompiler
import dev.romainguy.kotlin.explorer.build.KotlinCompiler
import dev.romainguy.kotlin.explorer.bytecode.ByteCodeParser
import dev.romainguy.kotlin.explorer.code.CodeContent
import dev.romainguy.kotlin.explorer.dex.DexDumpParser
import dev.romainguy.kotlin.explorer.oat.OatDumpParser
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
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
    onLogs: (String) -> Unit,
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
                    onLogs(kotlinc.output.replace(path.parent.toString() + "/", ""))
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
                onLogs(java.output)
                val status = if (java.exitCode != 0) "Error running code" else "Run completed"
                updater.advance(status)
            }

        } finally {
            withContext(ui) {
                updater.finish()
            }
        }
    }
}

suspend fun buildAndDisassemble(
    toolPaths: ToolPaths,
    source: String,
    onByteCode: (CodeContent) -> Unit,
    onDex: (CodeContent) -> Unit,
    onOat: (CodeContent) -> Unit,
    onLogs: (String) -> Unit,
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
                    onLogs(kotlinc.output.replace(path.parent.toString() + "/", ""))
                    updater.advance("Error compiling Kotlin", updater.steps)
                })
                return@launch
            }
            updater.addJob(launch(ui) { updater.advance("Kotlin compiled") })

            updater.addJob(launch {
                val javap = byteCodeDecompiler.decompile(directory)
                withContext(ui) {
                    val status = if (javap.exitCode != 0) {
                        onLogs(javap.output)
                        "Error Disassembling Java ByteCode"
                    } else {
                        onByteCode(byteCodeParser.parse(javap.output))
                        "Disassembled Java ByteCode"
                    }
                    updater.advance(status)
                }
            })

            writeR8Rules(directory)

            val r8 = process(
                *buildR8Command(toolPaths, directory, optimize),
                directory = directory
            )

            if (r8.exitCode != 0) {
                updater.addJob(launch(ui) {
                    onLogs(r8.output)
                    updater.advance("Error creating DEX", 5)
                })
                return@launch
            }
            updater.addJob(launch(ui) {
                updater.advance(if (optimize) "Optimized DEX with R8" else "Compiled DEX with D8")
            })

            updater.addJob(launch {
                val dexdump = process(
                    toolPaths.dexdump.toString(),
                    "-d",
                    "classes.dex",
                    directory = directory
                )
                withContext(ui) {
                    val status = if (dexdump.exitCode != 0) {
                        onLogs(dexdump.output)
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
                    onLogs(push.output)
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
                    onLogs(dex2oat.output)
                    updater.advance("Ready", 2)
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
                onLogs(oatdump.output)
                "Error creating oat dump"
            } else {
                "Created oat dump"
            }
            withContext(ui) { updater.advance(status) }
        } finally {
            updater.finish()
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

private fun buildR8Command(
    toolPaths: ToolPaths,
    directory: Path,
    optimize: Boolean
): Array<String> {
    val command = if (optimize) {
        mutableListOf(
            "java",
            "-classpath",
            toolPaths.d8.toString(),
            "com.android.tools.r8.R8",
            "--min-api",
            "21",
            "--pg-conf",
            "rules.txt",
            "--output",
            ".",
            "--lib",
            toolPaths.platform.toString()
        )
    } else {
        mutableListOf(
            "java",
            "-classpath",
            toolPaths.d8.toString(),
            "com.android.tools.r8.D8",
            "--min-api",
            "21",
            "--output",
            ".",
            "--lib",
            toolPaths.platform.toString()
        ).apply {
            toolPaths.kotlinLibs.forEach { jar ->
                this += "--lib"
                this += jar.toString()
            }
        }
    }

    val classFiles = Files
        .list(directory)
        .filter { path -> path.extension == "class" }
        .map { file -> file.fileName.toString() }
        .sorted()
        .collect(Collectors.toList())
    command.addAll(classFiles)

    if (optimize) {
        toolPaths.kotlinLibs.forEach { jar ->
            command += jar.toString()
        }
    }

    return command.toTypedArray()
}

private fun writeR8Rules(directory: Path) {
    // Match $ANDROID_HOME/tools/proguard/proguard-android-optimize.txt
    Files.writeString(
        directory.resolve("rules.txt"),
        """-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
        -optimizationpasses 5
        -allowaccessmodification
        -dontpreverify
        -dontobfuscate
        -keep,allow optimization class !kotlin.**,!kotlinx.** {
          <methods>;
        }""".trimIndent()
    )
}

private fun cleanupClasses(directory: Path) {
    Files
        .list(directory)
        .filter { path -> path.extension == "class" }
        .forEach { path -> path.toFile().delete() }
}

internal val BuiltInKotlinClass = Regex("^(kotlin|kotlinx|java|javax|org\\.(intellij|jetbrains))\\..+")

