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

fun buildCode(indent: Int, lineNumberMode: LineNumberMode, builderAction: CodeBuilder.() -> Unit): CodeBuilder {
    return CodeBuilder(indent, lineNumberMode).apply(builderAction)
}

/**
 * Builds a [Code] model
 *
 * This class maintains a state allowing it to build the `jump` table `line-number` list
 * that will be used by the UI to display jump markers and line number annotations.
 */
class CodeBuilder(
    private val indent: Int,
    private val lineNumberMode: LineNumberMode,
) {
    private var line = 0
    private val sb = StringBuilder()
    private val jumps = mutableMapOf<Int, Int>()

    // These 2 fields collect method scope data. They are reset when a method is added
    private val methodAddresses = mutableMapOf<Int, Int>()
    private val methodJumps = mutableListOf<Pair<Int, Int>>()

    fun getJumps(): Map<Int, Int> = jumps

    fun startClass(clazz: Class) {
        writeLine(clazz.header)
    }

    fun startMethod(method: Method) {
        sb.append(" ".repeat(indent))
        writeLine(method.header)
    }

    fun endMethod() {
        methodJumps.forEach { (line, address) ->
            val targetLine = methodAddresses[address] ?: return@forEach
            jumps[line] = targetLine
        }
        methodAddresses.clear()
        methodJumps.clear()
        writeLine("")
    }

    fun writeInstruction(instruction: Instruction) {
        sb.append("  ".repeat(indent))
        methodAddresses[instruction.address] = line
        if (instruction.jumpAddress != null) {
            methodJumps.add(line to instruction.jumpAddress)
        }
        if (lineNumberMode is FixedWidth) {
            val lineNumber = instruction.lineNumber
            val prefix = if (lineNumber != null) "$lineNumber:" else " "
            sb.append(prefix.padEnd(lineNumberMode.width + 2))
        }
        writeLine(instruction.code)
    }

    override fun toString() = sb.toString()

    private fun writeLine(text: String) {
        sb.append(text)
        sb.append('\n')
        line++
    }

    sealed class LineNumberMode {
        data object None : LineNumberMode()
        class FixedWidth(val width: Int) : LineNumberMode()
        // todo: Maybe add an AutoWidth mode that derives the width from the max line number.
    }
}