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

package dev.romainguy.kotlin.explorer.bytecode

import dev.romainguy.kotlin.explorer.PeekingIterator
import dev.romainguy.kotlin.explorer.code.*
import dev.romainguy.kotlin.explorer.code.CodeContent.Error
import dev.romainguy.kotlin.explorer.code.CodeContent.Success
import dev.romainguy.kotlin.explorer.consumeUntil
import dev.romainguy.kotlin.explorer.getValue

/*
 * Example:
 * ```
 * public final class KotlinExplorerKt {
 * ```
 */
private val ClassRegex = Regex("^.* class [_a-zA-Z][_\\w]+ \\{$")

/**
 * Examples:
 *
 * ```
 *      4: if_icmpge     31
 *     10: ifne          16
 *     13: goto          25
 * ```
 */
private val JumpRegex = Regex("^(goto|if[_a-z]*) +(?<address>\\d+)$")

class ByteCodeParser {
    fun parse(text: String): CodeContent {
        return try {
            val lines = PeekingIterator(text.lineSequence().iterator())

            val classes = buildList {
                while (lines.hasNext()) {
                    val match = lines.consumeUntil(ClassRegex) ?: break
                    val clazz = lines.readClass(match.value)
                    add(clazz)
                }
            }
            Success(classes)
        } catch (e: Exception) {
            Error(e)
        }
    }

    private fun PeekingIterator<String>.readClass(classHeader: String): Class {
        val methods = buildList {
            add(readMethod()) // There is always at least one method
            while (hasNext()) {
                when (next()) {
                    "}" -> break
                    "" -> add(readMethod())
                }
            }
        }
        return Class(classHeader, methods)
    }

    private fun PeekingIterator<String>.readMethod(): Method {
        val header = next().trim()
        next() // "  Code:"
        val instructions = readInstructions()
        val lineNumbers = readLineNumbers()

        return Method(header, instructions.withLineNumbers(lineNumbers))
    }

    private fun PeekingIterator<String>.readInstructions(): List<Instruction> {
        return buildList {
            while (hasNext()) {
                val line = next().trim()
                if (line == "LineNumberTable:") {
                    break
                }
                val (address, code) = line.split(": ", limit = 2)
                val jumpAddress = JumpRegex.matchEntire(code)?.getValue("address")?.toInt()

                add(Instruction(address.toInt(), line, jumpAddress))
            }
        }
    }

    private fun PeekingIterator<String>.readLineNumbers(): Map<Int, Int> {
        return buildMap {
            while (hasNext()) {
                val line = next().trim()
                if (!line.startsWith("line")) {
                    break
                }
                val (lineNumber, address) = line.substringAfter(' ').split(": ", limit = 2)
                put(address.toInt(), lineNumber.toInt())
            }
        }
    }
}
