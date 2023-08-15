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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.system.exitProcess

class ToolPaths {
    val tempDirectory = Files.createTempDirectory("kotlin-explorer")
    val androidHome =  Paths.get(System.getenv("ANDROID_HOME") ?: "<unknown>")
    val kotlinHome =  Paths.get(System.getenv("KOTLIN_HOME") ?: "<unknown>")
    val buildToolsDirectory: Path
    val platform: Path
    val d8: Path
    val adb: Path
    val kotlinc: Path
    val kotlinLibs: List<Path>
    val settingsDirectory: Path
    val sourceFile: Path

    init {
        if (!androidHome.exists()) {
            println("\$ANDROID_HOME missing or invalid: $androidHome")
            exitProcess(1)
        }
        adb = androidHome.resolve("platform-tools/adb")
        buildToolsDirectory = Files
            .list(androidHome.resolve("build-tools"))
            .sorted { p1, p2 ->
                p2.toString().compareTo(p1.toString())
            }
            .findFirst()
            .get()
        d8 = buildToolsDirectory.resolve("lib/d8.jar")

        val platformsDirectory = Files
            .list(androidHome.resolve("platforms"))
            .sorted { p1, p2 ->
                p2.toString().compareTo(p1.toString())
            }
            .findFirst()
            .get()
        platform = platformsDirectory.resolve("android.jar")

        if (!kotlinHome.exists()) {
            println("\$KOTLIN_HOME missing or invalid: $kotlinHome")
            exitProcess(1)
        }

        kotlinc = kotlinHome.resolve("bin/kotlinc")

        val lib = kotlinHome.resolve("lib")
        kotlinLibs = listOf(
            lib.resolve("kotlin-stdlib-jdk8.jar"),
            lib.resolve("kotlin-stdlib.jar"),
            lib.resolve("kotlin-annotations-jvm.jar"),
            Files
                .list(lib)
                .filter { path ->
                    path.extension == "jar" && path.fileName.toString().startsWith("annotations-")
                }
                .sorted()
                .findFirst()
                .get()
        )

        settingsDirectory = Paths.get(System.getProperty("user.home"), ".kotlin-explorer")
        if (!settingsDirectory.exists()) {
            Files.createDirectory(settingsDirectory)
        }
        sourceFile = settingsDirectory.resolve("source-code.kt")
    }
}
