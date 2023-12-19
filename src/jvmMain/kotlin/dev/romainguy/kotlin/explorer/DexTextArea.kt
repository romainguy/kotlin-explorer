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

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import java.awt.Graphics
import java.awt.Graphics2D

private val JumpPattern = Regex("(\\s+)[0-9a-fA-F]{4}: .+([0-9a-fA-F]{4}) // ([+-])[0-9a-fA-F]{4}[\\n\\r]*")
private val AddressedPattern = Regex("(\\s+)([0-9a-fA-F]{4}): .+[\\n\\r]*")

class DexTextArea : RSyntaxTextArea() {
    private var displayJump = false
    private var jumpRange = 0 to 0
    private var horizontalOffsets = 0 to 0

    init {
        addCaretListener { event ->
            val oldDisplayJump = displayJump
            val oldJumpRange = jumpRange
            val oldHorizontalOffsets = horizontalOffsets

            displayJump = false

            val srcLine = getLineOfOffset(event.dot)
            var start = getLineStartOffset(srcLine)
            var end = getLineEndOffset(srcLine)

            var line = document.getText(start, end - start)
            var result = JumpPattern.matchEntire(line)
            if (result != null) {
                val srcHorizontalOffset = start + result.groupValues[1].length
                val targetAddress = result.groupValues[2]
                val direction = if (result.groupValues[3] == "+") 1 else -1

                var dstLine = srcLine + direction
                while (dstLine in 0..<lineCount) {
                    start = getLineStartOffset(dstLine)
                    end = getLineEndOffset(dstLine)
                    line = document.getText(start, end - start)

                    result = AddressedPattern.matchEntire(line)
                    if (result == null) {
                        break
                    } else if (result.groupValues[2] == targetAddress) {
                        val dstHorizontalOffset = start + result.groupValues[1].length

                        displayJump = true
                        jumpRange = srcLine to dstLine
                        horizontalOffsets = srcHorizontalOffset to dstHorizontalOffset
                    }

                    dstLine += direction
                }
            }

            if (displayJump != oldDisplayJump ||
                jumpRange != oldJumpRange ||
                horizontalOffsets != oldHorizontalOffsets) {
                repaint()
            }
        }
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)

        if (displayJump) {
            val padding = 6

            val bounds1 = modelToView2D(horizontalOffsets.first)
            val bounds2 = modelToView2D(horizontalOffsets.second)

            val x1 = bounds1.x.toInt() - padding
            val y1 = (bounds1.y + lineHeight / 2).toInt()

            val x2 = bounds2.x.toInt() - padding
            val y2 = (bounds2.y + lineHeight / 2).toInt()

            val g2 = g as Graphics2D
            g2.drawLine(x1, y1, x1 / 2, y1)
            g2.drawLine(x1 / 2, y1, x2 / 2, y2)
            g2.drawLine(x2 / 2, y2, x2, y2)
        }
    }
}
