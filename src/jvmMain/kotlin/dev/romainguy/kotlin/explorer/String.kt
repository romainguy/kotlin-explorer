/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.romainguy.kotlin.explorer

fun Iterator<String>.consumeUntil(prefix: String): Boolean {
    while (hasNext()) {
        val line = next()
        if (line.trim().startsWith(prefix)) return true
    }
    return false
}

fun Iterator<String>.consumeUntil(regex: Regex): MatchResult? {
    while (hasNext()) {
        val line = next()
        val match = regex.matchEntire(line)
        if (match != null) {
            return match
        }
    }
    return null
}
