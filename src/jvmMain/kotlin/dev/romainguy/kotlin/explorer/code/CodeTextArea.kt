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

package dev.romainguy.kotlin.explorer.code

import dev.romainguy.kotlin.explorer.code.CodeBuilder.LineNumberMode
import dev.romainguy.kotlin.explorer.code.CodeBuilder.LineNumberMode.FixedWidth
import dev.romainguy.kotlin.explorer.code.CodeBuilder.LineNumberMode.None
import dev.romainguy.kotlin.explorer.code.CodeContent.*
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.GeneralPath
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener

open class CodeTextArea(
    presentationMode: Boolean = false,
    private val indent: Int = 4,
    lineNumberMode: LineNumberMode = FixedWidth(4),
) : RSyntaxTextArea() {
    private var code: Code? = null
    private var jumpOffsets: JumpOffsets? = null

    var content: CodeContent = Empty
        set(value) {
            field = value
            updateContent()
        }

    var presentationMode = presentationMode
        set(value) {
            field = value
            repaint()
        }

    var lineNumberMode = lineNumberMode
        set(value) {
            field = value
            val line = getLineOfOffset(caretPosition)
            updateContent()
            caretPosition = getLineStartOffset(line)
            caretUpdate(line)
        }

    init {
        addCaretListener(::caretUpdate)
    }

    private fun updateContent() {
        code = null
        when (val content = content) {
            is Empty -> text = ""
            is Error -> text = content.errorText
            is Success -> code = Code(content.classes, indent, lineNumberMode).also {
                val text = it.text
                if (text != this.text) {
                    this.text = text
                }
            }
        }
    }

    final override fun addCaretListener(listener: CaretListener?) {
        super.addCaretListener(listener)
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        jumpOffsets?.let { jump ->
            val scale = if (presentationMode) 2 else 1
            val padding = 6 * scale
            val triangleSize = 8 * scale

            val bounds1 = modelToView2D(jump.src)
            val bounds2 = modelToView2D(jump.dst)

            val x1 = bounds1.x.toInt() - padding
            val y1 = (bounds1.y + lineHeight / 2).toInt()

            val delta = jump.dst - getLineStartOffset(getLineOfOffset(jump.dst))
            val showLineNumbers = lineNumberMode !is None
            val endPadding = if (showLineNumbers && delta < 4) 2 else padding

            val x2 = bounds2.x.toInt() - endPadding
            val y2 = (bounds2.y + lineHeight / 2).toInt()

            val x0 = if (showLineNumbers) {
                modelToView2D(minOf(4, jump.dst - 4)).x.toInt() + padding
            } else {
                modelToView2D(4).x.toInt()
                modelToView2D(4).x.toInt()
            }

            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.stroke = BasicStroke(scale.toFloat())
            g2.drawLine(x1, y1, x0, y1)
            g2.drawLine(x0, y1, x0, y2)
            g2.drawLine(x0, y2, x2 - triangleSize / 2, y2)

            g2.fill(GeneralPath().apply {
                val fx = x2.toFloat()
                val fy = y2.toFloat() + 0.5f
                val fs = triangleSize.toFloat()
                moveTo(fx, fy)
                lineTo(fx - fs, fy - fs / 2.0f)
                lineTo(fx - fs, fy + fs / 2.0f)
            })
        }
    }

    private fun caretUpdate(event: CaretEvent) {
        caretUpdate(getLineOfOffset(event.dot))
    }

    private fun caretUpdate(line: Int) {
        val codeModel = code ?: return
        val oldJumpOffsets = jumpOffsets
        try {
            jumpOffsets = null
            val dstLine = codeModel.getJumpTargetOfLine(line) ?: return

            val srcOffset = getLineStartOffset(line) + getLine(line).countPadding()
            val dstOffset = getLineStartOffset(dstLine) + getLine(dstLine).countPadding()
            jumpOffsets = JumpOffsets(srcOffset, dstOffset)
        } finally {
            if (jumpOffsets != oldJumpOffsets) {
                repaint()
            }
        }
    }

    private fun getLine(line: Int): String {
        val start = getLineStartOffset(line)
        val end = getLineEndOffset(line)
        return document.getText(start, end - start).trimEnd()
    }

    private data class JumpOffsets(val src: Int, val dst: Int)
}

private fun String.countPadding() = indexOfFirst { it != ' ' }
