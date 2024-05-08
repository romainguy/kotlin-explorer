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

private const val Debug = 1
private const val Warning = 2

object Logger {
    private val level = System.getenv("KOTLIN_EXPLORER_LOG")?.toIntOrNull() ?: 0

    fun debug(message: String) {
        if (level >= Debug) {
            println("Debug:  $message")
        }
    }

    fun warn(message: String) {
        if (level >= Warning) {
            println("Warning: $message")
        }
    }
}