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

package dev.romainguy.kotlin.explorer.code

import java.io.ByteArrayOutputStream
import java.io.PrintStream

sealed class CodeContent {
    data class Success(val classes: List<Class>) : CodeContent()
    data class Error(val errorText: String) : CodeContent() {
        constructor(e: Exception) : this(e.toFullString())
    }
    data object Empty : CodeContent()
}

private fun Throwable.toFullString(): String {
    return ByteArrayOutputStream().use {
        printStackTrace(PrintStream(it))
        it.toString()
    }
}
