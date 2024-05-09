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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.NoOpUpdate
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.image.BufferedImage
import javax.swing.JTextArea
import javax.swing.JViewport

/**
 * A [SwingPanel] that supports a Dialog rendered over it
 *
 * When [isDialogVisible] is true, the panel captures a screenshot its [Component] and renders an [Image] instead of
 * the actual [SwingPanel]. The [SwingPanel] is still rendered in order to keep it attached to the component hierarchy.
 */
@Composable
fun <T : Component> DialogSupportingSwingPanel(
    background: Color = Color.White,
    factory: () -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = NoOpUpdate,
    isDialogVisible: Boolean,
) {
    val component = remember { factory() }

    // TODO: Remove all this code
    // See https://github.com/JetBrains/compose-multiplatform-core/pull/915
    // macOS should work, but it doesn't quite yet, mouse events get dispatched only to the Swing panel below
    if (isLinux || isMac) {
        if (isDialogVisible) {
            Column(modifier = modifier) {
                val bitmap = remember { component.getScreenShot()?.toComposeImageBitmap() }
                if (bitmap != null) {
                    Image(bitmap = bitmap, contentDescription = "", modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier.fillMaxSize())
                }
            }
        }
        SwingPanel(background, { component }, modifier = Modifier.fillMaxSize(), update)
    } else {
        SwingPanel(background, { component }, modifier, update)
    }
}

fun JTextArea.centerCaretInView() {
    val viewport = parent as? JViewport ?: return
    val linePos = modelToView2D(caretPosition).bounds.centerY.toInt()
    viewport.viewPosition = Point(0, maxOf(0, linePos - viewport.height / 2))
}

private fun Component.getScreenShot(): BufferedImage? {
    if (width == 0 || height == 0) {
        return null
    }
    val config = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val image = config.createCompatibleImage(width, height)
    paint(image.graphics)
    return image
}
