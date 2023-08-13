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

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import java.nio.file.Path

class ProcessResult(
    val exitCode: Int,
    val output: String
)

suspend fun process(
    vararg command: String,
    directory: Path? = null
): ProcessResult {
    return withContext(Dispatchers.IO) {
        val process = ProcessBuilder()
            .directory(directory?.toFile())
            .command(*command)
            .redirectErrorStream(true)
            .start()

        val output = async {
            process
                .inputStream
                .bufferedReader()
                .lineSequence()
                .asFlow()
                .map { value ->
                    yield()
                    value
                }
                .toList()
                .joinToString(System.lineSeparator())
        }

        try {
            val exitCode = runInterruptible {
                process.waitFor()
            }
            val processOutput = output.await()
            ProcessResult(exitCode, processOutput)
        } catch (e: CancellationException) {
            throw e
        }
    }
}
