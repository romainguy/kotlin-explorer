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

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import javax.swing.JScrollPane
import javax.swing.JViewport

private const val FontSizeEditingMode = 12.0f
private const val FontSizePresentationMode = 20.0f

open class SyntaxTextArea : RSyntaxTextArea() {
    var presentationMode = false
        set(value) {
            if (field != value) {
                field = value
                updateFontSize()
            }
        }

    private fun updateFontSize() {
        val scheme = syntaxScheme

        val increaseRatio = if (presentationMode) {
            FontSizePresentationMode / FontSizeEditingMode
        } else {
            FontSizeEditingMode / FontSizePresentationMode
        }

        val count = scheme.styleCount
        for (i in 0 until count) {
            val ss = scheme.getStyle(i)
            if (ss != null) {
                val font = ss.font
                if (font != null) {
                    val oldSize: Float = font.size2D
                    val newSize: Float = oldSize * increaseRatio
                    ss.font = font.deriveFont(newSize)
                }
            }
        }

        font = font.deriveFont(if (presentationMode) FontSizePresentationMode else FontSizeEditingMode)

        syntaxScheme = scheme
        var parent = parent
        if (parent is JViewport) {
            parent = parent.parent
            if (parent is JScrollPane) {
                parent.repaint()
            }
        }
    }
}