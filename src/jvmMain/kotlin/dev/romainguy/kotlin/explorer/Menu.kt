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

@file:Suppress("FunctionName")

package dev.romainguy.kotlin.explorer

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuScope
import kotlin.reflect.KMutableProperty0

/** Convenience class handles `Ctrl <-> Meta` modifies */
sealed class Shortcut(private val key: Key, private val isShift: Boolean, private val isCtrl: Boolean) {
    class Ctrl(key: Key) : Shortcut(key, isCtrl = true, isShift = false)
    class CtrlShift(key: Key) : Shortcut(key, isCtrl = true, isShift = true)

    fun asKeyShortcut() = KeyShortcut(key = key, ctrl = isCtrl && !isMac, shift = isShift, meta = isCtrl && isMac)
}

@Composable
fun MenuScope.MenuCheckboxItem(
    text: String,
    shortcut: Shortcut,
    property: KMutableProperty0<Boolean>,
    onCheckedChanged: (Boolean) -> Unit = {}
) {
    CheckboxItem(text, property.get(), shortcut = shortcut.asKeyShortcut(), onCheckedChange = {
        property.set(it)
        onCheckedChanged(it)
    })
}

@Composable
fun MenuScope.MenuItem(text: String, shortcut: Shortcut, onClick: () -> Unit) {
    Item(text, shortcut = shortcut.asKeyShortcut(), onClick = onClick)
}
