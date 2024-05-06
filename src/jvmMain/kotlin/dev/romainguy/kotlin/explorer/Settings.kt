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

@file:Suppress("FunctionName")

package dev.romainguy.kotlin.explorer

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun Settings(
    explorerState: ExplorerState,
    onSaveClick: () -> Unit
) {
    val androidHome = remember { mutableStateOf(explorerState.androidHome) }
    val kotlinHome = remember { mutableStateOf(explorerState.kotlinHome) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.align(Alignment.Center)) {
            StringSetting("Android home directory: ", androidHome) { explorerState.toolPaths.isAndroidHomeValid }
            StringSetting("Kotlin home directory: ", kotlinHome) { explorerState.toolPaths.isKotlinHomeValid }

            DefaultButton({ explorerState.saveState(androidHome, kotlinHome, onSaveClick) }) {
                Text("Save")
            }
        }
    }
}

private fun ExplorerState.saveState(
    androidHome: MutableState<String>,
    kotlinHome: MutableState<String>,
    onSaveClick: () -> Unit
) {
    this.androidHome = androidHome.value
    this.kotlinHome = kotlinHome.value
    this.reloadToolPathsFromSettings()
    onSaveClick()
}

@Composable
private fun StringSetting(title: String, state: MutableState<String>, isValid: () -> Boolean) {
    Row {
        Text(
            title,
            modifier = Modifier
                .alignByBaseline()
                .defaultMinSize(minWidth = 160.dp),
        )
        TextField(
            value = state.value,
            onValueChange = { state.value = it },
            modifier = Modifier
                .alignByBaseline()
                .defaultMinSize(minWidth = 360.dp),
            trailingIcon = { if (isValid()) ValidIcon() else ErrorIcon() }
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ErrorIcon() {
    Icon(
        "icons/error.svg",
        iconClass = ExplorerState::class.java,
        contentDescription = "Error",
        tint = Color(0xffee4056)
    )
}

@Composable
private fun ValidIcon() {
    Icon(
        "icons/done.svg",
        iconClass = ExplorerState::class.java,
        contentDescription = "Valid",
        tint = Color(0xff3369d6)
    )
}
