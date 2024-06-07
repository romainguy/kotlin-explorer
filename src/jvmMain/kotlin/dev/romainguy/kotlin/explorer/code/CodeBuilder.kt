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

import androidx.collection.IntIntPair
import androidx.collection.mutableIntIntMapOf

fun buildCode(codeStyle: CodeStyle = CodeStyle(), builderAction: CodeBuilder.() -> Unit): CodeBuilder {
    return CodeBuilder(codeStyle).apply(builderAction)
}

/**
 * Builds a [Code] model
 *
 * This class maintains a state allowing it to build the `jump` table `line-number` list
 * that will be used by the UI to display jump markers and line number annotations.
 */
class CodeBuilder(private val codeStyle: CodeStyle) {
    private var line = 0
    private val sb = StringBuilder()
    private val jumps = mutableIntIntMapOf()
    private val sourceToCodeLine = mutableIntIntMapOf()
    private val codeToSourceToLine = mutableIntIntMapOf()

    // These 3 fields collect method scope data. They are reset when a method is added
    private val methodAddresses = mutableIntIntMapOf()
    private val methodJumps = mutableListOf<IntIntPair>()
    private var lastMethodLineNumber: Int = -1

    fun startClass(clazz: Class) {
        writeLine(clazz.header)
    }

    fun writeMethod(method: Method) {
        startMethod(method)
        val instructionSet = method.instructionSet
        instructionSet.instructions.forEach { instruction ->
            writeInstruction(instructionSet, instruction)
        }
        endMethod()
    }

    private fun startMethod(method: Method) {
        sb.append(" ".repeat(codeStyle.indent))
        writeLine(method.header)

        sb.append("  ".repeat(codeStyle.indent))
        val instructionCount = method.instructionSet.instructions.size
        writeLine("-- $instructionCount instruction${if (instructionCount > 1) "s" else ""}")

        val branches = countBranches(method.instructionSet)
        if (branches > 0) {
            sb.append("  ".repeat(codeStyle.indent))
            writeLine("-- $branches branch${if (branches > 1) "es" else ""}")
        }
    }

    private fun countBranches(instructionSet: InstructionSet): Int {
        var count = 0
        val branchInstructions = instructionSet.isa.branchInstructions
        instructionSet.instructions.forEach { instruction ->
            val code = instruction.code
            val start = code.indexOf(": ") + 2
            val end = code.indexOfFirst(start) { c -> !c.isLetter() }
            val opCode = code.substring(start, end)
            if (branchInstructions.contains(opCode)) {
                count++
            }
        }
        return count
    }

    private fun endMethod() {
        methodJumps.forEach { (line, address) ->
            val targetLine = methodAddresses.getOrDefault(address, -1)
            if (targetLine == -1) return@forEach
            jumps[line] = targetLine
        }
        methodAddresses.clear()
        methodJumps.clear()
        lastMethodLineNumber = -1
    }

    private fun writeInstruction(instructionSet: InstructionSet, instruction: Instruction) {
        sb.append("  ".repeat(codeStyle.indent))

        methodAddresses[instruction.address] = line
        if (instruction.jumpAddress > -1) {
            methodJumps.add(IntIntPair(line, instruction.jumpAddress))
        }

        val lineNumber = instruction.lineNumber
        if (lineNumber > -1) {
            sourceToCodeLine[lineNumber] = line
            lastMethodLineNumber = lineNumber
        }

        codeToSourceToLine[line] = lastMethodLineNumber
        if (codeStyle.showLineNumbers) {
            val prefix = if (lineNumber > -1) "$lineNumber:" else " "
            sb.append(prefix.padEnd(codeStyle.lineNumberWidth + 2))
        }

        sb.append(instruction.code)

        if (instruction.callAddress != -1) {
            val callReference = instructionSet.methodReferences[instruction.callAddress]
            if (callReference != null) {
                sb.append(" → ").append(callReference.name)
            }
        }

        sb.append('\n')
        line++
    }

    fun build(): Code {
        return Code(sb.toString(), jumps, sourceToCodeLine, codeToSourceToLine)
    }

    override fun toString() = sb.toString()

    fun writeLine(text: String) {
        sb.append(text)
        sb.append('\n')
        line++
    }
}

private inline fun CharSequence.indexOfFirst(start: Int, predicate: (Char) -> Boolean): Int {
    val end = length
    for (index in start..<end ) {
        if (predicate(this[index])) {
            return index
        }
    }
    return end
}
