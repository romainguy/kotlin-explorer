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
    private val jumps = mutableMapOf<Int, Int>()
    private val sourceToCodeLine = mutableMapOf<Int, Int>()
    private val codeToSourceToLine = mutableMapOf<Int, Int>()

    // These 3 fields collect method scope data. They are reset when a method is added
    private val methodAddresses = mutableMapOf<Int, Int>()
    private val methodJumps = mutableListOf<Pair<Int, Int>>()
    private var lastMethodLineNumber: Int = -1

    fun startClass(clazz: Class) {
        writeLine(clazz.header)
    }

    fun startMethod(method: Method) {
        sb.append(" ".repeat(codeStyle.indent))
        writeLine(method.header)
        sb.append("  ".repeat(codeStyle.indent))
        writeLine("-- ${method.instructionSet.instructions.size} instructions")
        val branches = countBranches(method.instructionSet)
        if (branches > 0) {
            sb.append("  ".repeat(codeStyle.indent))
            writeLine("-- $branches branch${if (branches > 1) "es" else ""}")
        }
    }

    private fun countBranches(instructionSet: InstructionSet): Int {
        var count = 0
        instructionSet.instructions.forEach { instruction ->
            val code = instruction.code
            val index = code.indexOf(": ")
            instructionSet.isa.branchInstructions.forEach out@ { opCode ->
                if (code.startsWith(opCode, index + 2)) {
                    count++
                    return@out
                }
            }
        }
        return count
    }

    fun endMethod() {
        methodJumps.forEach { (line, address) ->
            val targetLine = methodAddresses[address] ?: return@forEach
            jumps[line] = targetLine
        }
        methodAddresses.clear()
        methodJumps.clear()
        lastMethodLineNumber = -1

        writeLine("")
    }

    fun writeInstruction(instruction: Instruction) {
        sb.append("  ".repeat(codeStyle.indent))
        methodAddresses[instruction.address] = line
        if (instruction.jumpAddress != null) {
            methodJumps.add(line to instruction.jumpAddress)
        }
        val lineNumber = instruction.lineNumber
        if (lineNumber != null) {
            sourceToCodeLine[lineNumber] = line
            lastMethodLineNumber = lineNumber
        }
        codeToSourceToLine[line] = lastMethodLineNumber
        if (codeStyle.showLineNumbers) {
            val prefix = if (lineNumber != null) "$lineNumber:" else " "
            sb.append(prefix.padEnd(codeStyle.lineNumberWidth + 2))
        }
        writeLine(instruction.code)
    }

    fun build(): Code {
        return Code(sb.toString(), jumps, sourceToCodeLine, codeToSourceToLine)
    }

    override fun toString() = sb.toString()

    private fun writeLine(text: String) {
        sb.append(text)
        sb.append('\n')
        line++
    }
}