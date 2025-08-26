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

import dev.romainguy.kotlin.explorer.code.CodeTextArea
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class SourceTextArea(
    var isSyncLinesEnabled: Boolean
) : SyntaxTextArea() {
    private val codeTextAreas = mutableListOf<CodeTextArea>()

    init {
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (isSyncLinesEnabled) {
                    codeTextAreas.forEach {
                        it.gotoSourceLine(getLineOfOffset(viewToModel2D(e.point)))
                    }
                }
            }
        })
    }

    fun addCodeTextAreas(vararg codeTextAreas: CodeTextArea) {
        this.codeTextAreas.addAll(codeTextAreas)
    }

    fun gotoLine(src: CodeTextArea, line: Int) {
        caretPosition = getLineStartOffset(line.coerceIn(0 until lineCount))
        centerCaretInView()
        // Sync other `CodeTextArea` to same line as the `src` sent
        codeTextAreas.filter { it !== src }.forEach { it.gotoSourceLine(line) }
    }
}
