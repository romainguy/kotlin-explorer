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
import kotlin.io.path.*

class ToolPaths(settingsDirectory: Path, androidHome: Path, kotlinHome: Path) {
    constructor(settingsDirectory: Path, androidHome: String, kotlinHome: String) : this(
        settingsDirectory,
        Path.of(androidHome),
        Path.of(kotlinHome)
    )
    val tempDirectory = Files.createTempDirectory("kotlin-explorer")!!
    val platform: Path
    val d8: Path
    val adb: Path = androidHome.resolve(if (isWindows) "platform-tools/adb.exe" else "platform-tools/adb")
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
        val buildToolsDirectory = androidHome.resolve("build-tools")
            .listIfExists()
            .maxByOrNull { it.pathString }
            ?: androidHome
        d8 = System.getenv("D8_PATH")?.toPath() ?: buildToolsDirectory.resolve("lib/d8.jar")
        dexdump = buildToolsDirectory.resolve(if (isWindows) "dexdump.exe" else "dexdump")

        val platformsDirectory = androidHome.resolve("platforms")
            .listIfExists()
            .maxByOrNull { it.pathString }
            ?: androidHome
        platform = platformsDirectory.resolve("android.jar")

        kotlinc = kotlinHome.resolve(if (isWindows) "bin/kotlinc.bat" else "bin/kotlinc")

        val lib = kotlinHome.resolveFirstExistsOrFirst("lib", "libexec/lib")
        kotlinLibs = listOf(
            lib.resolve("kotlin-stdlib-jdk8.jar"),
            lib.resolve("kotlin-stdlib.jar"),
            lib.resolve("kotlin-annotations-jvm.jar"),
            lib.resolve("kotlinx-coroutines-core-jvm.jar"),
            lib.listIfExists()
                .filter { path -> path.extension == "jar" && path.name.startsWith("annotations-") }
                .maxByOrNull { it.pathString }
                ?: lib.resolve("annotations.jar")
        )

        sourceFile = settingsDirectory.resolve("source-code.kt")

        isAndroidHomeValid = adb.exists() && d8.exists() && dexdump.exists()
        isKotlinHomeValid = kotlinc.exists()
        isValid = adb.exists() && d8.exists() && dexdump.exists() && kotlinc.exists()
    }
}

private fun Path.listIfExists() = if (exists()) listDirectoryEntries() else emptyList()

private fun String.toPath() = Path.of(this)

private fun Path.resolveFirstExistsOrFirst(vararg others: String): Path {
    for (other in others) {
        val path = resolve(other)
        if (path.exists()) {
            return path
        }
    }

    return resolve(others.first())
}
