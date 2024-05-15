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

import androidx.compose.ui.graphics.Color
import org.jetbrains.skiko.SystemTheme

val ErrorColor = Color(0xffa04646)
val ProgressColor = Color(0xff3369d6)
val ProgressTrackColor = Color(0xffc4c4c4)

enum class KotlinExplorerTheme {
    Dark, Light, System;

    // TODO: Using currentSystemTheme leads to an UnsatisfiedLinkError with the JetBrains Runtime
    fun isDark() = (if (this == System) fromSystemTheme(/* currentSystemTheme */ SystemTheme.LIGHT) else this) == Dark

    companion object {
        fun fromSystemTheme(systemTheme: SystemTheme) = if (systemTheme == SystemTheme.LIGHT) Light else Dark
    }
}
