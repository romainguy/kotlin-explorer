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

import dev.romainguy.kotlin.explorer.SourceTextArea
import dev.romainguy.kotlin.explorer.centerCaretInView
import dev.romainguy.kotlin.explorer.code.CodeContent.*
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.GeneralPath
import javax.swing.event.CaretEvent

class CodeTextArea(
    presentationMode: Boolean = false,
    codeStyle: CodeStyle,
    var isSyncLinesEnabled: Boolean,
    private val sourceTextArea: SourceTextArea?,
) : RSyntaxTextArea() {
    private var code: Code? = null
    private var jumpOffsets: JumpOffsets? = null
    private var content: CodeContent = Empty

    var presentationMode = presentationMode
        set(value) {
            field = value
            repaint()
        }

    var codeStyle = codeStyle
        set(value) {
            val changed = value != field
            field = value
            if (changed) {
                updatePreservingCaretLine()
            }
        }

    init {
        addCaretListener(::caretUpdate)

        if (sourceTextArea != null) {
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    val codeLine = getLineOfOffset(viewToModel2D(event.point))
                    val line = code?.getSourceLine(codeLine) ?: return
                    sourceTextArea.gotoLine(this@CodeTextArea, line - 1)
                }
            })
        }
    }


    fun setContent(value: CodeContent) {
        content = value
        updateContent()
    }

    fun gotoSourceLine(sourceLine: Int) {
        val line = code?.getCodeLine(sourceLine + 1) ?: return
        caretPosition = getLineStartOffset(line.coerceIn(0 until lineCount))
        centerCaretInView()
    }

    private fun updatePreservingCaretLine() {
        val line = getLineOfOffset(caretPosition)
        val oldText = text
        updateContent()
        if (oldText != text) {
            caretPosition = getLineStartOffset(line)
            caretUpdate(line)
        }
    }

    private fun updateContent() {
        val position = caretPosition
        code = null
        when (val content = content) {
            is Empty -> text = ""
            is Error -> text = content.errorText
            is Success -> code = Code.fromClasses(content.classes, codeStyle).also {
                val text = it.text
                if (text != this.text) {
                    this.text = text
                }
            }
        }
        caretPosition = minOf(position, document.length)
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
            val endPadding = if (codeStyle.showLineNumbers && delta < 4) 2 else padding

            val x2 = bounds2.x.toInt() - endPadding
            val y2 = (bounds2.y + lineHeight / 2).toInt()

            val x0 = modelToView2D(maxOf(codeStyle.indent * 2 - 4, 1)).x.toInt()

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
