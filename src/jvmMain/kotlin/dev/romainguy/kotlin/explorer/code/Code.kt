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

/**
 * A data model representing disassembled code
 *
 * Given a list [Class]'s constructs a mode that provides:
 * * Disassembled text with optional line number annotations
 * * Jump information for branch instructions
 */
class Code(private val classes: List<Class>, indent: Int, lineNumberMode: LineNumberMode) {
    private val jumps: Map<Int, Int>

    val text: String

    init {
        val code = buildCode(indent, lineNumberMode) {
            classes.forEach { clazz ->
                startClass(clazz)
                clazz.methods.forEach { method ->
                    startMethod(method)
                    method.instructions.forEach { instruction ->
                        writeInstruction(instruction)
                    }
                    endMethod()
                }
            }
        }
        text = code.toString()
        jumps = code.getJumps()
    }

    fun getJumpTargetOfLine(line: Int) = jumps[line]
}
