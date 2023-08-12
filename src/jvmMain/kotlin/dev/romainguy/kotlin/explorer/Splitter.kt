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

@file:OptIn(ExperimentalSplitPaneApi::class)

package dev.romainguy.kotlin.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitterScope
import java.awt.Cursor

private fun Modifier.cursorForHorizontalResize(): Modifier =
    pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
private fun Modifier.cursorForVerticalResize(): Modifier =
    pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))

fun SplitterScope.VerticalSplitter() {
    visiblePart {
        Box(
            Modifier
                .height(5.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colors.background)
        )
    }
    handle {
        Box(
            Modifier
                .markAsHandle()
                .cursorForVerticalResize()
                .background(MaterialTheme.colors.background)
                .height(5.dp)
                .fillMaxWidth()
        )
    }
}

fun SplitterScope.HorizontalSplitter() {
    visiblePart {
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colors.background)
        )
    }
    handle {
        Box(
            Modifier
                .markAsHandle()
                .cursorForHorizontalResize()
                .background(MaterialTheme.colors.background)
                .width(5.dp)
                .fillMaxHeight()
        )
    }
}
