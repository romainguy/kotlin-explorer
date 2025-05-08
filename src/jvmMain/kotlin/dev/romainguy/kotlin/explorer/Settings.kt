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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icon.PathIconKey


@Composable
fun Settings(
    state: ExplorerState,
    onSaveRequest: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val androidHome = rememberTextFieldState(state.androidHome)
    val kotlinHome = rememberTextFieldState(state.kotlinHome)
    val kotlinOnlyConsumers = remember { mutableStateOf(state.kotlinOnlyConsumers) }
    val compilerFlags = rememberTextFieldState(state.compilerFlags)
    val r8rules = rememberTextFieldState(state.r8Rules)
    val composeVersion = rememberTextFieldState(state.composeVersion)
    val minApi = rememberTextFieldState(state.minApi.toString())
    val indent = rememberTextFieldState(state.indent.toString())
    val lineNumberWidth = rememberTextFieldState(state.lineNumberWidth.toString())
    val decompileHiddenIsa = remember { mutableStateOf(state.decompileHiddenIsa) }
    val onSaveClick = {
        state.saveState(
            androidHome.text.toString(),
            kotlinHome.text.toString(),
            kotlinOnlyConsumers.value,
            compilerFlags.text.toString(),
            r8rules.text.toString(),
            composeVersion.text.toString(),
            minApi.text.toString(),
            indent.text.toString(),
            lineNumberWidth.text.toString(),
            decompileHiddenIsa.value
        )
        onSaveRequest()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
        val toolPaths = ToolPaths(state.directory, androidHome.text.toString(), kotlinHome.text.toString())
        StringSetting("Android home directory: ", androidHome) { toolPaths.isAndroidHomeValid }
        StringSetting("Kotlin home directory: ", kotlinHome) { toolPaths.isKotlinHomeValid }
        IntSetting("Decompiled code indent: ", indent, minValue = 2)
        IntSetting("Line number column width: ", lineNumberWidth, minValue = 1)
        StringSetting("Kotlin compiler flags: ", compilerFlags)
        MultiLineStringSetting("R8 rules: ", r8rules)
        StringSetting("Compose version: ", composeVersion) {composeVersion.text.isNotEmpty()}
        IntSetting("Min API: ", minApi, minValue = 1)
        BooleanSetting("Kotlin only consumers", kotlinOnlyConsumers)
        BooleanSetting("Decompile hidden instruction sets", decompileHiddenIsa)
        Spacer(modifier = Modifier.height(8.dp))
        Buttons(saveEnabled = toolPaths.isValid, onSaveClick, onDismissRequest)
    }
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
    androidHome: String,
    kotlinHome: String,
    kotlinOnlyConsumers: Boolean,
    compilerFlags: String,
    r8Rules: String,
    composeVersion: String,
    minApi: String,
    indent: String,
    lineNumberWidth: String,
    decompileHiddenIsa: Boolean,
) {
    this.androidHome = androidHome
    this.kotlinHome = kotlinHome
    this.kotlinOnlyConsumers = kotlinOnlyConsumers
    this.compilerFlags = compilerFlags
    this.r8Rules = r8Rules
    this.composeVersion = composeVersion
    this.minApi = minApi.toIntOrNull() ?: 21
    this.indent = indent.toIntOrNull() ?: 4
    this.lineNumberWidth = lineNumberWidth.toIntOrNull() ?: 4
    this.decompileHiddenIsa = decompileHiddenIsa
    this.reloadToolPathsFromSettings()
}

@Composable
private fun StringSetting(title: String, state: TextFieldState, isValid: () -> Boolean = { true }) {
    SettingRow(title, state, isValid)
}

@Composable
private fun ColumnScope.MultiLineStringSetting(title: String, state: TextFieldState) {
    Row(Modifier.weight(1.0f)) {
        Text(
            title,
            modifier = Modifier
                .alignByBaseline()
                .defaultMinSize(minWidth = 200.dp),
        )
        TextArea(
            state,
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight(),
            lineLimits = TextFieldLineLimits.MultiLine(10, 10)
        )
    }
}

@Composable
private fun IntSetting(title: String, state: TextFieldState, minValue: Int) {
    val isValid by derivedStateOf {
        (state.text.toString().toIntOrNull() ?: Int.MIN_VALUE) >= minValue
    }

    SettingRow(title, state, isValid = { isValid }, {
        val changed = this.toString()
        if (changed.isNotEmpty() && changed.toIntOrNull() == null) {
            revertAllChanges()
        }
    })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BooleanSetting(@Suppress("SameParameterValue") title: String, state: MutableState<Boolean>) {
    Row {
        Checkbox(state.value, onCheckedChange = { state.value = it })
        Text(
            title,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 2.dp)
                .onClick {
                    state.value = !state.value
                }
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    state: TextFieldState,
    isValid: () -> Boolean,
    inputTransformation: InputTransformation? = null
) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            title,
            modifier = Modifier
                .alignByBaseline()
                .defaultMinSize(minWidth = 200.dp),
        )
        TextField(
            state,
            modifier = Modifier
                .alignByBaseline()
                .defaultMinSize(minWidth = 360.dp)
                .weight(1.0f),
            inputTransformation = inputTransformation,
            trailingIcon = { if (isValid()) ValidIcon() else ErrorIcon() }
        )
    }
}

@Composable
private fun ErrorIcon() {
    Icon(
        key = PathIconKey(
            "icons/error.svg",
            iconClass = ExplorerState::class.java
        ), contentDescription = "Error",
        tint = IconErrorColor,
        hints = arrayOf()
    )
}

@Composable
private fun ValidIcon() {
    Icon(
        key = PathIconKey(
            "icons/done.svg",
            iconClass = ExplorerState::class.java
        ), contentDescription = "Valid",
        tint = IconValidColor,
        hints = arrayOf()
    )
}
