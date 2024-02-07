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
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.jvm.optionals.getOrElse

fun createToolPaths(settings: Settings): ToolPaths {
    val androidHome = Paths.get(settings.entries.getOrElse("ANDROID_HOME") {
        System.getenv("ANDROID_HOME") ?: System.getProperty("user.home")
    })
    val kotlinHome = Paths.get(settings.entries.getOrElse("KOTLIN_HOME") {
        System.getenv("KOTLIN_HOME") ?: System.getProperty("user.home")
    })

    return ToolPaths(settings, androidHome, kotlinHome)
}

class ToolPaths internal constructor(
    settings: Settings,
    val androidHome: Path,
    val kotlinHome: Path
) {
    val tempDirectory = Files.createTempDirectory("kotlin-explorer")!!
    val platform: Path
    val d8: Path
    val adb: Path
    val dexdump: Path
    val kotlinc: Path
    val kotlinLibs: List<Path>
    val sourceFile: Path

    var isValid: Boolean = false
        private set
    var isAndroidHomeValid: Boolean = false
        private set
    var isKotlinHomeValid: Boolean = false
        private set

    init {
        adb = androidHome.resolve(if (isWindows) "platform-tools/adb.exe" else "platform-tools/adb")
        val buildToolsDirectory = listIfExists(androidHome.resolve("build-tools"))
            .sorted { p1, p2 ->
                p2.toString().compareTo(p1.toString())
            }
            .findFirst()
            .getOrElse { androidHome }
        d8 = buildToolsDirectory.resolve("lib/d8.jar")
        dexdump = buildToolsDirectory.resolve(if (isWindows) "dexdump.exe" else "dexdump")

        val platformsDirectory = listIfExists(androidHome.resolve("platforms"))
            .sorted { p1, p2 ->
                p2.toString().compareTo(p1.toString())
            }
            .findFirst()
            .getOrElse { androidHome }
        platform = platformsDirectory.resolve("android.jar")

        kotlinc = kotlinHome.resolve("bin/kotlinc")

        val lib = kotlinHome.resolve("lib")
        kotlinLibs = listOf(
            lib.resolve("kotlin-stdlib-jdk8.jar"),
            lib.resolve("kotlin-stdlib.jar"),
            lib.resolve("kotlin-annotations-jvm.jar"),
            lib.resolve("kotlinx-coroutines-core-jvm.jar"),
            listIfExists(lib)
                .filter { path ->
                    path.extension == "jar" && path.fileName.toString().startsWith("annotations-")
                }
                .sorted()
                .findFirst()
                .getOrElse { lib.resolve("annotations.jar") }
        )

        sourceFile = settings.directory.resolve("source-code.kt")

        isAndroidHomeValid = adb.exists() && d8.exists() && dexdump.exists()
        isKotlinHomeValid = kotlinc.exists()
        isValid = adb.exists() && d8.exists() && dexdump.exists() && kotlinc.exists()
    }
}

private fun listIfExists(path: Path) = if (path.exists()) {
    Files.list(path)
} else {
    Stream.empty()
}
