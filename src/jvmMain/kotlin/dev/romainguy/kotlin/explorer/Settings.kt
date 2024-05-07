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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun Settings(
    explorerState: ExplorerState,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        SettingsContent(explorerState, onDismissRequest)
    }
}

@Composable
private fun SettingsContent(
    state: ExplorerState,
    onDismissRequest: () -> Unit
) {
    val androidHome = remember { mutableStateOf(state.androidHome) }
    val kotlinHome = remember { mutableStateOf(state.kotlinHome) }
    val indent = remember { mutableStateOf(state.indent.toString()) }
    val lineNumberWidth = remember { mutableStateOf(state.lineNumberWidth.toString()) }
    val onSaveClick = {
        state.saveState(androidHome, kotlinHome, indent, lineNumberWidth)
        onDismissRequest()
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        // TODO: Use a theme
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            Title()

            val toolPaths = ToolPaths(state.directory, androidHome.value, kotlinHome.value)
            StringSetting("Android home directory: ", androidHome) { toolPaths.isAndroidHomeValid }
            StringSetting("Kotlin home directory: ", kotlinHome) { toolPaths.isKotlinHomeValid }
            IntSetting("Decompiled Code indent: ", indent, minValue = 2)
            IntSetting("Line number column width: ", lineNumberWidth, minValue = 1)

            Spacer(modifier = Modifier.height(32.dp))
            Buttons(saveEnabled = toolPaths.isValid, onSaveClick, onDismissRequest)
        }
    }
}

@Composable
private fun Title() {
    Text(
        "Settings",
        fontSize = 24.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    )
}

@Composable
private fun ColumnScope.Buttons(
    saveEnabled: Boolean,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.align(Alignment.End)) {
        DefaultButton(onClick = onCancelClick) {
            Text("Cancel")
        }
        DefaultButton(enabled = saveEnabled, onClick = onSaveClick) {
            Text("Save")
        }
    }

}

private fun ExplorerState.saveState(
    androidHome: MutableState<String>,
    kotlinHome: MutableState<String>,
    indent: MutableState<String>,
    lineNumberWidth: MutableState<String>,
) {
    this.androidHome = androidHome.value
    this.kotlinHome = kotlinHome.value
    this.indent = indent.value.toInt()
    this.lineNumberWidth = lineNumberWidth.value.toInt()
    this.reloadToolPathsFromSettings()
}

@Composable
private fun StringSetting(title: String, state: MutableState<String>, isValid: () -> Boolean) {
    SettingRow(title, state.value, { state.value = it }, isValid)
}

@Composable
private fun IntSetting(title: String, state: MutableState<String>, minValue: Int) {

    SettingRow(
        title,
        value = state.value,
        onValueChange = {
            if (it.toIntOrNull() != null || it.isEmpty()) {
                state.value = it
            }
        },
        isValid = { (state.value.toIntOrNull() ?: Int.MIN_VALUE) >= minValue }
    )
}

@Composable
private fun SettingRow(title: String, value: String, onValueChange: (String) -> Unit, isValid: () -> Boolean) {
    Row {
        Text(
            title,
            modifier = Modifier
                .alignByBaseline()
                .defaultMinSize(minWidth = 200.dp),
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .alignByBaseline()
                .defaultMinSize(minWidth = 360.dp),
            trailingIcon = { if (isValid()) ValidIcon() else ErrorIcon() }
        )
    }
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
