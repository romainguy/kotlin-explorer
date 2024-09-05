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

package dev.romainguy.kotlin.explorer.testing

import dev.romainguy.kotlin.explorer.ToolPaths
import dev.romainguy.kotlin.explorer.build.ByteCodeDecompiler
import dev.romainguy.kotlin.explorer.build.KotlinCompiler
import dev.romainguy.kotlin.explorer.bytecode.ByteCodeParser
import dev.romainguy.kotlin.explorer.code.CodeContent
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.test.fail

interface Builder {
    fun generateByteCode(testFile: String): String

    companion object {
        fun getInstance(outputDirectory: Path) =
            try {
                LocalBuilder(outputDirectory)
            } catch (e: Throwable) {
                System.err.println("Failed to create local builder. Using Github builder")
                GithubBuilder()
            }
    }
}

class GithubBuilder : Builder {
    private val cwd = Path.of(System.getProperty("user.dir"))
    private val testData = cwd.resolve("src/jvmTest/kotlin/testData")

    override fun generateByteCode(testFile: String): String {
        return testData.resolve(testFile.replace(".kt", ".javap")).readText()
    }
}

class LocalBuilder(private val outputDirectory: Path) : Builder {
    private val cwd = Path.of(System.getProperty("user.dir"))
    private val testData = cwd.resolve("src/jvmTest/kotlin/testData")
    private val toolPaths = createToolsPath()
    private val kotlinCompiler = KotlinCompiler(toolPaths, outputDirectory)
    private val byteCodeDecompiler = ByteCodeDecompiler()

    override fun generateByteCode(testFile: String): String {
        val path = testData.resolve(testFile)
        if (path.notExists()) {
            fail("$path does not exists")
        }
        return runBlocking {
            kotlinCompile(path)
            val result = byteCodeDecompiler.decompile(outputDirectory)
            if (result.exitCode != 0) {
                System.err.println(result.output)
                fail("javap error")
            }

            // Save the fine under <project/build/testData> so we can examine it when needed
            val saveFile = testData.resolve(testFile.replace(".kt", ".javap"))
            if (saveFile.parent.notExists()) {
                saveFile.parent.createDirectory()
            }
            saveFile.writeText(result.output)

            result.output
        }
    }

    private suspend fun kotlinCompile(path: Path) {
        val result = kotlinCompiler.compile("", path)
        if (result.exitCode != 0) {
            System.err.println(result.output)
            fail("kotlinc error")
        }
    }

    private fun createToolsPath(): ToolPaths {
        val properties = Properties()
        properties.load(cwd.resolve("local.properties").reader())

        val kotlinHome = getKotlinHome(properties)
        val androidHome = getAndroidHome(properties)
        val toolPaths = ToolPaths(Path.of(""), androidHome, kotlinHome)
        if (toolPaths.isKotlinHomeValid && toolPaths.isAndroidHomeValid) {
            return toolPaths
        }
        throw IllegalStateException("Invalid ToolsPath: KOTLIN_HOME=${kotlinHome} ANDROID_HOME=$androidHome")
    }
}

private fun getKotlinHome(properties: Properties): Path {
    val pathString = System.getenv("KOTLIN_HOME") ?: properties.getProperty("kotlin.home")

    if (pathString == null) {
        throw IllegalStateException("Could not find Android SDK")
    }
    val path = Path.of(pathString)
    if (path.notExists()) {
        throw IllegalStateException("Could not find Android SDK")
    }
    return path
}

private fun getAndroidHome(properties: Properties): Path {
    val path =
        when (val androidHome: String? = System.getenv("ANDROID_HOME") ?: properties.getProperty("android.home")) {
            null -> Path.of(System.getProperty("user.home")).resolve("Android/Sdk")
            else -> Path.of(androidHome)
        }

    if (path.notExists()) {
        throw IllegalStateException("Could not find Android SDK")
    }
    return path
}

private fun CodeContent.asSuccess(): CodeContent.Success {
    if (this !is CodeContent.Success) {
        fail("Expected Success but got: $this")
    }
    return this
}

fun ByteCodeParser.parseSuccess(text: String) = parse(text).asSuccess()