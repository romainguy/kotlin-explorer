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

import dev.romainguy.kotlin.explorer.code.CodeContent
import dev.romainguy.kotlin.explorer.code.CodeContent.Error
import dev.romainguy.kotlin.explorer.dex.DexDumpParser
import dev.romainguy.kotlin.explorer.oat.OatDumpParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.extension

private const val TotalSteps = 5
private const val Done = 1f

private val dexDumpParser = DexDumpParser()
private val oatDumpParser = OatDumpParser()

suspend fun disassemble(
    toolPaths: ToolPaths,
    source: String,
    onDex: (CodeContent) -> Unit,
    onOat: (CodeContent) -> Unit,
    onStatusUpdate: (String, Float) -> Unit,
    optimize: Boolean
) = coroutineScope {
    val ui = currentCoroutineContext()

    launch(Dispatchers.IO) {
        var step = 0f

        launch(ui) { onStatusUpdate("Compiling Kotlin…", step++ / TotalSteps) }

        val directory = toolPaths.tempDirectory
        cleanupClasses(directory)

        val path = directory.resolve("KotlinExplorer.kt")
        Files.writeString(path, source)

        val kotlinc = process(
            *buildKotlincCommand(toolPaths, path),
            directory = directory
        )

        if (kotlinc.exitCode != 0) {
            launch(ui) {
                onDex(Error(kotlinc.output.replace(path.parent.toString() + "/", "")))
                onStatusUpdate("Ready", Done)
            }
            return@launch
        }

        launch(ui) {
            val status = if (optimize) "Optimizing with R8…" else "Compiling with D8…"
            onStatusUpdate(status, step++ / TotalSteps)
        }

        writeR8Rules(directory)

        val r8 = process(
            *buildR8Command(toolPaths, directory, optimize),
            directory = directory
        )

        if (r8.exitCode != 0) {
            launch(ui) {
                onDex(Error(r8.output))
                onStatusUpdate("Ready", Done)
            }
            return@launch
        }

        launch(ui) { onStatusUpdate("Disassembling DEX…", step++ / TotalSteps) }

        val dexdump = process(
            toolPaths.dexdump.toString(),
            "-d",
            "classes.dex",
            directory = directory
        )

        if (dexdump.exitCode != 0) {
            launch(ui) {
                onDex(Error(dexdump.output))
                onStatusUpdate("Ready", Done)
            }
            return@launch
        }

        launch(ui) {
            onDex(dexDumpParser.parse(dexdump.output))
            onStatusUpdate("AOT compilation…", step++ / TotalSteps)
        }

        val push = process(
            toolPaths.adb.toString(),
            "push",
            "classes.dex",
            "/sdcard/classes.dex",
            directory = directory
        )

        if (push.exitCode != 0) {
            launch(ui) {
                onOat(Error(push.output))
                onStatusUpdate("Ready", Done)
            }
            return@launch
        }

        val dex2oat = process(
            toolPaths.adb.toString(),
            "shell",
            "dex2oat",
            "--dex-file=/sdcard/classes.dex",
            "--oat-file=/sdcard/classes.oat",
            directory = directory
        )

        if (dex2oat.exitCode != 0) {
            launch(ui) {
                onOat(Error(dex2oat.output))
                onStatusUpdate("Ready", Done)
            }
            return@launch
        }

        launch(ui) { onStatusUpdate("Disassembling OAT…", step++ / TotalSteps) }

        val oatdump = process(
            toolPaths.adb.toString(),
            "shell",
            "oatdump",
            "--oat-file=/sdcard/classes.oat",
            directory = directory
        )

        launch(ui) { onOat(oatDumpParser.parse(oatdump.output)) }

        if (oatdump.exitCode != 0) {
            launch(ui) { onStatusUpdate("Ready", Done) }
            return@launch
        }

        launch(ui) { onStatusUpdate("Ready", Done) }
    }
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

private fun buildKotlincCommand(toolPaths: ToolPaths, path: Path): Array<String> {
    val command = mutableListOf(
        toolPaths.kotlinc.toString(),
        path.toString(),
        "-Xmulti-platform",
        "-Xno-param-assertions",
        "-Xno-call-assertions",
        "-Xno-receiver-assertions",
        "-classpath",
        toolPaths.kotlinLibs.joinToString(":") { jar -> jar.toString() }
            + ":${toolPaths.platform}"
    )

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
        -keep,allowoptimization class !kotlin.**,!kotlinx.** {
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

