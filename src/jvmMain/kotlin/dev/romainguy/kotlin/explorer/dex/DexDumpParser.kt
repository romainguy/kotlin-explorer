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

package dev.romainguy.kotlin.explorer.dex

import dev.romainguy.kotlin.explorer.BuiltInKotlinClass
import dev.romainguy.kotlin.explorer.HexDigit
import dev.romainguy.kotlin.explorer.code.*
import dev.romainguy.kotlin.explorer.code.CodeContent.Error
import dev.romainguy.kotlin.explorer.code.CodeContent.Success
import dev.romainguy.kotlin.explorer.consumeUntil
import dev.romainguy.kotlin.explorer.getValue

private val PositionRegex = Regex("^\\s*0x(?<address>[0-9a-f]+) line=(?<line>\\d+)$")

private val JumpRegex = Regex("^$HexDigit{4}: .* (?<address>$HexDigit{4}) // [+-]$HexDigit{4}$")

private const val ClassStart = "Class #"
private const val ClassEnd = "source_file_idx"
private const val ClassName = "Class descriptor"
private const val Instructions = "insns size"
private const val Positions = "positions"

internal class DexDumpParser {

    fun parse(text: String): CodeContent {
        return try {
            val lines = text.lineSequence().iterator()
            val classes = buildList {
                while (lines.consumeUntil(ClassStart)) {
                    val clazz = lines.readClass()
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

    private fun Iterator<String>.readClass(): Class? {
        val className = next().getClassName()
        if (className.matches(BuiltInKotlinClass)) {
            return null
        }
        val methods = buildList {
            while (hasNext()) {
                val line = next().trim()
                when {
                    line.startsWith(ClassEnd) -> break
                    line.startsWith(Instructions) -> add(readMethod(className))
                }
            }
        }
        return Class("class $className", methods)
    }

    private fun Iterator<String>.readMethod(className: String): Method {
        val (name, type) = next().substringAfterLast(".").split(':', limit = 2)
        val instructions = readInstructions()
        consumeUntil(Positions)
        val positions = readPositions()
        return Method("$name$type // $className.$name()", instructions.withLineNumbers(positions))
    }

    private fun Iterator<String>.readInstructions(): List<Instruction> {
        return buildList {
            while (hasNext()) {
                val line = next()
                if (line.startsWith(" ")) {
                    break
                }
                val code = line.substringAfter('|')
                val address = code.substringBefore(": ")
                val jumpAddress = JumpRegex.matchEntire(code)?.getValue("address")
                add(Instruction(address.toInt(16), code, jumpAddress?.toInt(16)))
            }
        }
    }

    private fun Iterator<String>.readPositions(): Map<Int, Int> {
        return buildMap {
            while (hasNext()) {
                val line = next()
                val match = PositionRegex.matchEntire(line) ?: break
                put(match.getValue("address").toInt(16), match.getValue("line").toInt())
            }
        }
    }

}

private fun String.getClassName() =
    getValue(ClassName)
        .removePrefix("L")
        .removeSuffix(";")
        .replace('/', '.')

private fun String.getValue(name: String): String {
    if (!trim().startsWith(name)) {
        throw IllegalStateException("Expected '$name'")
    }
    return substringAfter('\'').substringBefore('\'')
}
