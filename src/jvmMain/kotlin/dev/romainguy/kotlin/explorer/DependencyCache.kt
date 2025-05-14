/*
 * Copyright (C) 2025 Romain Guy
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

import java.io.FileNotFoundException
import java.net.URL
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.util.zip.ZipInputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

private const val MAVEN_REPO = "https://repo1.maven.org/maven2"
private const val GOOGLE_REPO = "https://maven.google.com"
private val repos = listOf(MAVEN_REPO, GOOGLE_REPO)

private const val BASE_PATH = "%1\$s/%2\$s/%3\$s/%2\$s-%3\$s"

class DependencyCache(private val root: Path) {

    fun getDependency(dependency: Dependency, onOutput: (String) -> Unit): Path {
        return getDependency(dependency.group, dependency.name, dependency.version, onOutput)
    }

    fun getDependency(group: String, name: String, version: String, onOutput: (String) -> Unit): Path {
        val basePath = BASE_PATH.format(group.replace('.', '/'), name, version)
        val dst = root.resolve("$basePath.jar")
        if (dst.notExists()) {
            onOutput("Downloading artifact $group:$name:$version")
            repos.forEach {
                if (getDependency(it, basePath, dst, onOutput)) {
                    return dst
                }
            }
            onOutput("Could not find artifact $group:$name:$version")
        }
        return dst
    }

    /**
     * Try to download a jar from a repo.
     *
     * First tries to download the `jar` file directly. If the `jar` is not found, will try to download an `aar` and
     * extract the `classes.ja` file from it.
     *
     * @return true if the repo owns the artifact.
     */
    private fun getDependency(repo: String, basePath: String, dst: Path, onOutput: (String) -> Unit): Boolean {
        try {
            // Does the artifact exist in repo?
            URL("$repo/$basePath.pom").openStream().reader().close()
        } catch (_: FileNotFoundException) {
            return false
        }
        dst.createParentDirectories()
        dst.outputStream(CREATE, WRITE, TRUNCATE_EXISTING).use { outputStream ->
            try {
                URL("$repo/$basePath.jar").openStream().use {
                    it.copyTo(outputStream)
                }
            } catch (_: FileNotFoundException) {
                val aar = "$repo/$basePath.aar"

                ZipInputStream(URL(aar).openStream()).use {
                    while (true) {
                        val entry = it.nextEntry
                        if (entry == null) {
                            onOutput("Could not find 'classes.jar' in $aar")
                            return true
                        }
                        if (entry.name == "classes.jar") {
                            it.copyTo(outputStream)
                            break
                        }
                    }
                }

            }
        }
        return true
    }

    data class Dependency(val group: String, val name: String, val version: String)
}
