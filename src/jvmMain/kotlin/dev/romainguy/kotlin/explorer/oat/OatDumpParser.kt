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

package dev.romainguy.kotlin.explorer.oat

import androidx.collection.IntObjectMap
import androidx.collection.mutableIntObjectMapOf
import dev.romainguy.kotlin.explorer.*
import dev.romainguy.kotlin.explorer.code.*
import dev.romainguy.kotlin.explorer.code.CodeContent.Error
import dev.romainguy.kotlin.explorer.code.CodeContent.Success

private val ClassNameRegex = Regex("^\\d+: L(?<class>[^;]+); \\(offset=0x$HexDigit+\\) \\(type_idx=\\d+\\).+")
private val MethodRegex = Regex("^\\s+\\d+:\\s+(?<method>.+)\\s+\\(dex_method_idx=(?<methodIndex>\\d+)\\)")
private val CodeRegex = Regex("^\\s+0x(?<address>$HexDigit+):\\s+$HexDigit+\\s+(?<code>.+)")

private val DexCodeRegex = Regex("^\\s+0x(?<address>$HexDigit+):\\s+($HexDigit+\\s+)+\\|\\s+(?<code>.+)")
private val DexMethodInvokeRegex = Regex("^invoke-[^}]+},\\s+\\S+\\s+(?<name>.+)\\s+//.+")

private val Aarch64JumpRegex = Regex(".+ #[+-]0x$HexDigit+ \\(addr 0x(?<address>$HexDigit+)\\)\$")
private val X86JumpRegex = Regex(".+ [+-]\\d+ \\(0x(?<address>$HexDigit{8})\\)\$")

private val Aarch64MethodCallRegex = Regex("^blr lr$")
private val X86MethodCallRegex = Regex("^TODO$") // TODO: implement x86

private val DexMethodReferenceRegex = Regex("^\\s+StackMap.+dex_pc=0x(?<callAddress>$HexDigit+),.+$")
private val DexInlineInfoRegex = Regex("^\\s+InlineInfo.+dex_pc=0x(?<callAddress>$HexDigit+),\\s+method_index=(?<methodIndex>$HexDigit+).+$")

internal class OatDumpParser {
    private var isa = ISA.Aarch64

    fun parse(text: String): CodeContent {
        return try {
            val lines = PeekingIterator(text.lineSequence().iterator())
            val isa = when (val set = lines.readInstructionSet()) {
                "Arm64" -> ISA.Aarch64
                "X86_64" -> ISA.X86_64
                else -> throw IllegalStateException("Unknown instruction set: $set")
            }
            val jumpRegex = when (isa) {
                ISA.Aarch64 -> Aarch64JumpRegex
                ISA.X86_64 -> X86JumpRegex
                else -> throw IllegalStateException("Incompatible ISA: $isa")
            }
            val methodCallRegex = when (isa) {
                ISA.Aarch64 -> Aarch64MethodCallRegex
                ISA.X86_64 -> X86MethodCallRegex
                else -> throw IllegalStateException("Incompatible ISA: $isa")
            }
            val classes = buildList {
                while (lines.hasNext()) {
                    val match = lines.consumeUntil(ClassNameRegex) ?: break
                    val clazz = lines.readClass(
                        match.getValue("class").replace('/', '.'),
                        jumpRegex,
                        methodCallRegex
                    )
                    if (clazz != null && clazz.methods.isNotEmpty()) {
                        add(clazz)
                    }
                }
            }
            Success(classes)
        } catch (e: Exception) {
            Error(e)
        }
    }

    private fun PeekingIterator<String>.readInstructionSet(): String {
        consumeUntil("INSTRUCTION SET:")
        return next()
    }

    private fun PeekingIterator<String>.readClass(
        className: String,
        jumpRegex: Regex,
        methodCallRegex: Regex
    ): Class? {
        if (className.matches(BuiltInKotlinClass)) {
            return null
        }
        val methods = buildList {
            while (hasNext()) {
                val line = peek()
                when {
                    ClassNameRegex.matches(line) -> break
                    else -> {
                        // Skip to the next line first and then read the method
                        next()

                        val match = MethodRegex.matchEntire(line)
                        if (match != null) {
                            add(readMethod(match, jumpRegex, methodCallRegex))
                        }
                    }
                }
            }
        }
        return Class("class $className", methods)
    }

    private fun PeekingIterator<String>.readMethod(
        match: MatchResult,
        jumpRegex: Regex,
        methodCallRegex: Regex
    ): Method {
        consumeUntil("DEX CODE:")
        val methodReferences = readMethodReferences()

        consumeUntil("CODE:")
        val instructions = readNativeInstructions(jumpRegex, methodCallRegex)

        val method = match.getValue("method")
        val index = match.getValue("methodIndex").toInt()
        return Method(method, InstructionSet(isa, instructions, methodReferences), index)
    }

    private fun PeekingIterator<String>.readMethodReferences(): IntObjectMap<MethodReference> {
        val map = mutableIntObjectMapOf<MethodReference>()
        while (hasNext()) {
            val match = DexCodeRegex.matchEntire(next())
            if (match != null) {
                match.toMethodReference()?.apply {
                    map[address] = this
                }
            } else {
                break
            }
        }
        return map
    }

    private fun MatchResult.toMethodReference(): MethodReference? {
        val code = getValue("code")
        val nameMatch = DexMethodInvokeRegex.matchEntire(code)
        if (nameMatch != null) {
            val address = getValue("address")
            val name = nameMatch.getValue("name")
            val pc = address.toInt(16)
            return MethodReference(pc, name)
        }
        return null
    }

    private fun PeekingIterator<String>.readNativeInstructions(
        jumpRegex: Regex,
        methodCallRegex: Regex
    ): List<Instruction> {
        return buildList {
            while (hasNext()) {
                val line = peek()
                when {
                    line.matches(MethodRegex) -> break
                    line.matches(ClassNameRegex) -> break
                    else -> {
                        val match = CodeRegex.matchEntire(next())
                        if (match != null) {
                            add(
                                readNativeInstruction(
                                    this@readNativeInstructions,
                                    match,
                                    jumpRegex,
                                    methodCallRegex
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun readNativeInstruction(
        iterator: PeekingIterator<String>,
        match: MatchResult,
        jumpRegex: Regex,
        methodCallRegex: Regex
    ): Instruction {
        val address = match.getValue("address")
        val code = match.getValue("code")

        var callAddress = if (methodCallRegex.matches(code)) {
            DexMethodReferenceRegex.matchEntire(iterator.peek())?.getValue("callAddress")?.toInt(16) ?: -1
        } else {
            -1
        }

        val callAddressMethod = if (callAddress != -1) {
            // Skip the StackMap line
            iterator.next()
            // Check the InlineInfo if present
            val methodIndex = DexInlineInfoRegex.matchEntire(iterator.peek())
            if (methodIndex != null) {
                callAddress = methodIndex.getValue("callAddress").toInt(16)
                methodIndex.getValue("methodIndex").toInt()
            } else {
                -1
            }
        } else {
            -1
        }

        val jumpAddress = if (callAddress == -1) {
            jumpRegex.matchEntire(code)?.getValue("address")?.toInt(16) ?: -1
        } else {
            -1
        }

        val codeAddress = address.toInt(16)
        val (op, operands) = codeToOpAndOperands(code)
        return Instruction(codeAddress, "0x$address", op, operands, jumpAddress, callAddress, callAddressMethod)
    }
}

internal fun codeToOpAndOperands(code: String): Pair<String, String> {
    val index = code.indexOf(' ')
    val opCode = if (index >= 0) code.substring(0, index) else code
    val operands = if (index >= 0) code.substring(index + 1).trim() else ""
    return Pair(opCode, operands)
}
