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

import java.awt.Point
import javax.swing.JTextArea
import javax.swing.JViewport

fun JTextArea.centerCaretInView() {
    val viewport = parent as? JViewport ?: return
    val linePos = modelToView2D(caretPosition).bounds.centerY.toInt()
    viewport.viewPosition = Point(0, maxOf(0, linePos - viewport.height / 2))
}
