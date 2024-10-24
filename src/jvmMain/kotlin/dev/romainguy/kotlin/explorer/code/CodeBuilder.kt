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
import androidx.collection.IntObjectMap
import androidx.collection.mutableIntIntMapOf
import androidx.collection.mutableIntObjectMapOf
import kotlin.math.max

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
    private var isa = ISA.Aarch64
    private val instructions = mutableIntObjectMapOf<Instruction>()

    // These 3 fields collect method scope data. They are reset when a method is added
    private val methodAddresses = mutableIntIntMapOf()
    private val methodJumps = mutableListOf<IntIntPair>()
    private var lastMethodLineNumber: Int = -1

    private fun alignOpCodes(isa: ISA) = when (isa) {
        ISA.ByteCode -> true
        ISA.X86_64 -> true
        ISA.Aarch64 -> true
        else -> false
    }

    fun startClass(clazz: Class) {
        writeLine(clazz.header)
    }

    fun writeMethod(method: Method, indexedMethods: IntObjectMap<Method>) {
        startMethod(method)

        val instructionSet = method.instructionSet
        // TODO: We should do this only once
        isa = instructionSet.isa
        val opCodeLength = if (alignOpCodes(instructionSet.isa)) opCodeLength(instructionSet) else -1

        instructionSet.instructions.forEach { instruction ->
            writeInstruction(instructionSet, instruction, indexedMethods, opCodeLength)
        }

        endMethod()
    }

    private fun opCodeLength(instructionSet: InstructionSet): Int {
        var maxLength = 0
        instructionSet.instructions.forEach { instruction ->
            val opCode = instruction.op
            maxLength = max(maxLength, opCode.length)
        }
        return maxLength
    }

    private fun startMethod(method: Method) {
        sb.append(" ".repeat(codeStyle.indent))
        writeLine(method.header)

        val indent = "  ".repeat(codeStyle.indent)

        sb.append(indent)
        val codeSize = method.codeSize
        val instructionCount = method.instructionSet.instructions.size
        writeLine("-- $instructionCount instruction${if (instructionCount > 1) "s" else ""}${if (codeSize >= 0) " ($codeSize bytes)" else ""}")

        val (pre, post) = countBranches(method.instructionSet)
        val branches = pre + post
        if (branches > 0) {
            sb.append(indent)
            writeLine("-- $branches branch${if (branches > 1) "es" else ""} ($pre + $post)")
        }
    }

    private fun countBranches(instructionSet: InstructionSet): IntIntPair {
        var preReturnCount = 0
        var postReturnCount = 0
        var returnSeen = false

        val branchInstructions = instructionSet.isa.branchInstructions
        val returnInstructions = instructionSet.isa.returnInstructions

        instructionSet.instructions.forEach { instruction ->
            val opCode = instruction.op
            if (returnInstructions.contains(opCode)) {
                returnSeen = true
            } else {
                if (branchInstructions.contains(opCode)) {
                    if (returnSeen) {
                        postReturnCount++
                    } else {
                        preReturnCount++
                    }
                }
            }
        }

        return IntIntPair(preReturnCount, postReturnCount)
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

    private fun writeInstruction(
        instructionSet: InstructionSet,
        instruction: Instruction,
        indexedMethods: IntObjectMap<Method>,
        opCodeLength: Int
    ) {
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

        instructions[line] = instruction

        sb.append(instruction.label)
        sb.append(": ")
        sb.append(instruction.op)
        if (instruction.operands.isNotEmpty()) {
            sb.append(if (opCodeLength != -1) " ".repeat(opCodeLength - instruction.op.length + 1) else " ")
            sb.append(instruction.operands)
        }

        if (instruction.callAddress != -1) {
            val set = if (instruction.callAddressMethod == -1) {
                instructionSet
            } else {
                indexedMethods[instruction.callAddressMethod]?.instructionSet
            }
            val callReference = set?.methodReferences?.get(instruction.callAddress)
            if (callReference != null) {
                sb.append(" → ").append(callReference.name)
            }
        }

        sb.append('\n')
        line++
    }

    fun build(): Code {
        return Code(isa, sb.toString(), instructions, jumps, sourceToCodeLine, codeToSourceToLine)
    }

    override fun toString() = sb.toString()

    fun writeLine(text: String) {
        sb.append(text)
        sb.append('\n')
        line++
    }
}
