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

import dev.romainguy.kotlin.explorer.*
import dev.romainguy.kotlin.explorer.code.*
import dev.romainguy.kotlin.explorer.code.CodeContent.Error
import dev.romainguy.kotlin.explorer.code.CodeContent.Success

private val ClassNameRegex = Regex("^\\d+: L(?<class>[^;]+); \\(offset=0x$HexDigit+\\) \\(type_idx=\\d+\\).+")
private val MethodRegex = Regex("^\\s+\\d+:\\s+(?<method>.+)\\s+\\(dex_method_idx=\\d+\\)")
private val CodeRegex = Regex("^\\s+0x(?<address>$HexDigit+):\\s+$HexDigit+\\s+(?<code>.+)")
private val Arm64JumpRegex = Regex(".+ #[+-]0x$HexDigit+ \\(addr 0x(?<address>$HexDigit+)\\)\$")
private val X86JumpRegex = Regex(".+ [+-]\\d+ \\(0x(?<address>$HexDigit{8})\\)\$")

internal class OatDumpParser {
    private var isa = ISA.Arm64

    fun parse(text: String): CodeContent {
        return try {
            val lines = PeekingIterator(text.lineSequence().iterator())
            val isa = when (val set = lines.readInstructionSet()) {
                "Arm64" -> ISA.Arm64
                "X86_64" -> ISA.X86_64
                else -> throw IllegalStateException("Unknown instruction set: $set")
            }
            val jumpRegex = when (isa) {
                ISA.Arm64 -> Arm64JumpRegex
                ISA.X86_64 -> X86JumpRegex
                else -> throw IllegalStateException("Incompatible ISA: $isa")
            }
            val classes = buildList {
                while (lines.hasNext()) {
                    val match = lines.consumeUntil(ClassNameRegex) ?: break
                    val clazz = lines.readClass(match.getValue("class").replace('/', '.'), jumpRegex)
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

    private fun PeekingIterator<String>.readClass(className: String, jumpRegex: Regex): Class? {
        if (className.matches(BuiltInKotlinClass)) {
            return null
        }
        val methods = buildList {
            while (hasNext()) {
                val line = peek()
                when {
                    ClassNameRegex.matches(line) -> break
                    MethodRegex.matches(line) -> add(readMethod(jumpRegex))
                    else -> next()
                }
            }
        }
        return Class("class $className", methods)
    }

    private fun PeekingIterator<String>.readMethod(jumpRegex: Regex): Method {
        val match = MethodRegex.matchEntire(next()) ?: throw IllegalStateException("Should not happen")
        val method = match.getValue("method")
        consumeUntil("CODE:")
        val instructions = readInstructions(jumpRegex)
        return Method(method, InstructionSet(instructions, isa))
    }

    private fun PeekingIterator<String>.readInstructions(jumpRegex: Regex): List<Instruction> {
        return buildList {
            while (hasNext()) {
                val line = peek()
                when {
                    line.matches(MethodRegex) -> break
                    line.matches(ClassNameRegex) -> break
                    line.matches(CodeRegex) -> add(readInstruction(jumpRegex))
                    else -> next()
                }
            }
        }
    }

    private fun PeekingIterator<String>.readInstruction(jumpRegex: Regex): Instruction {
        val match = CodeRegex.matchEntire(next()) ?: throw IllegalStateException("Should not happen")
        val address = match.getValue("address")
        val code = match.getValue("code")
        val jumpAddress = jumpRegex.matchEntire(code)?.getValue("address")?.toInt(16)
        return Instruction(address.toInt(16), "0x$address: $code", jumpAddress)
    }
}
