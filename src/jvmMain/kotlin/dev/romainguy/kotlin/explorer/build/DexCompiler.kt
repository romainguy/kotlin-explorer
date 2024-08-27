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

import dev.romainguy.kotlin.explorer.ProcessResult
import dev.romainguy.kotlin.explorer.ToolPaths
import dev.romainguy.kotlin.explorer.process
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class DexCompiler(private val toolPaths: ToolPaths, private val outputDirectory: Path, private val r8rules: String) {
    suspend fun buildDex(optimize: Boolean, keepEverything: Boolean): ProcessResult {
        return process(*buildDexCommand(optimize, keepEverything), directory = outputDirectory)
    }

    suspend fun dumpDex() = process(
        toolPaths.dexdump.toString(),
        "-d",
        "classes.dex",
        directory = outputDirectory
    )

    @OptIn(ExperimentalPathApi::class)
    private fun buildDexCommand(optimize: Boolean, keepEverything: Boolean): Array<String> {
        writeR8Rules(keepEverything)

        return buildList {
            add("java")
            add("-classpath")
            add(toolPaths.d8.toString())
            add(if (optimize) "com.android.tools.r8.R8" else "com.android.tools.r8.D8")
            add("--min-api")
            add("21")
            if (optimize) {
                add("--pg-conf")
                add("rules.txt")
            }
            add("--output")
            add(".")
            add("--lib")
            add(toolPaths.platform.toString())
            if (!optimize) {
                toolPaths.kotlinLibs.forEach { path ->
                    add("--lib")
                    add(path.pathString)
                }
            }
            outputDirectory.walk()
                .map { path -> outputDirectory.relativize(path) }
                .filter { path -> path.extension == "class" && path.first().name != "META-INF" }
                .forEach { path -> add(path.pathString) }

            if (optimize) {
                addAll(toolPaths.kotlinLibs.map { it.toString() })
            }

        }.toTypedArray()
    }

    private fun writeR8Rules(keepEverything: Boolean) {
        // Match $ANDROID_HOME/tools/proguard/proguard-android-optimize.txt
        Files.writeString(
            outputDirectory.resolve("rules.txt"),
            buildString {
                append(
                    """
                        -optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
                        -optimizationpasses 5
                        -allowaccessmodification
                        -dontpreverify
                        -dontobfuscate
                    """.trimIndent()
                )
                if (keepEverything) {
                    append(
                        """
                        -keep,allowoptimization class !kotlin.**,!kotlinx.** {
                            <methods>;
                        }
                        """.trimIndent()
                    )
                } else {
                    append(
                        """
                        -keep,allowobfuscation @interface Keep
                        -keep @Keep class * {*;}
                        -keepclasseswithmembers class * {
                            @Keep <methods>;
                        }
                        -keepclasseswithmembers class * {
                            @Keep <fields>;
                        }
                        -keepclasseswithmembers class * {
                            @Keep <init>(...);
                        }
                        """.trimIndent()
                    )
                }
                append(r8rules)
            }
        )
    }
}

