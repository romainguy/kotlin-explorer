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

import dev.romainguy.kotlin.explorer.jump.JumpDetector
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener

open class CodeTextArea(
    private val explorerState: ExplorerState,
    private val jumpDetector: JumpDetector,
    private val lineNumberRegex: Regex?,
) : RSyntaxTextArea() {
    private var jumpOffsets: JumpOffsets? = null
    private var fullText = ""


    init {
        addCaretListener(::caretUpdate)
    }

    final override fun addCaretListener(listener: CaretListener?) {
        super.addCaretListener(listener)
    }

    override fun setText(text: String) {
        fullText = text
        refreshText()
    }

    fun refreshText() {
        val saveCaret = caretPosition
        super.setText(fullText.takeIf { explorerState.showLineNumbers } ?: fullText.removeLineLumbers())
        caretPosition = saveCaret
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        jumpOffsets?.let { jump ->
            val padding = 6

            val bounds1 = modelToView2D(jump.src)
            val bounds2 = modelToView2D(jump.dst)

            val x1 = bounds1.x.toInt() - padding
            val y1 = (bounds1.y + lineHeight / 2).toInt()

            val x2 = bounds2.x.toInt() - padding
            val y2 = (bounds2.y + lineHeight / 2).toInt()

            val x0 = modelToView2D(2).x.toInt()
            val g2 = g as Graphics2D
            g2.drawLine(x1, y1, x0, y1)
            g2.drawLine(x0, y1, x0, y2)
            g2.drawLine(x0, y2, x2, y2)
        }
    }

    private fun caretUpdate(event: CaretEvent) {
        val oldJumpOffsets = jumpOffsets
        jumpOffsets = null
        val srcLine = getLineOfOffset(event.dot)
        var start = getLineStartOffset(srcLine)
        var end = getLineEndOffset(srcLine)

        val caretLine = document.getText(start, end - start).trimEnd()
        val jump = jumpDetector.detectJump(caretLine)
        if (jump != null) {
            val srcOffset = start + caretLine.countPadding()

            var dstLine = srcLine + jump.direction
            while (dstLine in 0..<lineCount) {
                start = getLineStartOffset(dstLine)
                end = getLineEndOffset(dstLine)
                val line = document.getText(start, end - start).trimEnd()

                val addressed = jumpDetector.detectAddressed(line) ?: break
                if (addressed == jump.address) {
                    val dstOffset = start + line.countPadding()
                    jumpOffsets = JumpOffsets(srcOffset, dstOffset)
                }

                dstLine += jump.direction
            }
        }

        if (jumpOffsets != oldJumpOffsets) {
            repaint()
        }
    }

    private fun String.removeLineLumbers() =
        lineNumberRegex?.replace(this) {
            val (start, end) = it.groupValues.drop(1)
            " ".repeat(start.length) + end
        } ?: this

    private data class JumpOffsets(val src: Int, val dst: Int)
}

private fun String.countPadding() = indexOfFirst { it != ' ' }
