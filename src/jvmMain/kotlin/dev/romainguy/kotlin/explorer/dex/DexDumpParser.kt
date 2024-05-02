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
import dev.romainguy.kotlin.explorer.consumeUntil

private val PositionRegex = Regex("^\\s*0x(?<address>[0-9a-f]+) line=(?<line>\\d+)$")

private const val ClassStart = "Class #"
private const val ClassEnd = "source_file_idx"
private const val ClassName = "Class descriptor"
private const val Instructions = "insns size"
private const val Positions = "positions"

internal class DexDumpParser(text: String) {
    private val lines = text.lineSequence().iterator()
    fun parseDexDump(): String {
        val classes = buildList {
            while (lines.consumeUntil(ClassStart)) {
                add(readClass())
            }
        }
        return classes
            .filter { it.methods.isNotEmpty() && !it.name.matches(BuiltInKotlinClass)}
            .joinToString(separator = "\n") { it.toString() }
    }

    private fun readClass(): DexClass {
        val className = lines.next().getClassName()
        val methods = buildList {
            while (lines.hasNext()) {
                val line = lines.next().trim()
                when {
                    line.startsWith(ClassEnd) -> break
                    line.startsWith(Instructions) -> add(readMethod(className))
                }
            }
        }
        return DexClass(className, methods)
    }

    private fun readMethod(className: String): DexMethod {
        val (name, type) = lines.next().substringAfterLast(".").split(':', limit = 2)
        val instructions = readInstructions()
        lines.consumeUntil(Positions)
        val positions = readPositions()
        val code = buildString {
            instructions.forEach {
                val lineNumber = positions[it.address]
                val prefix = if (lineNumber != null) "%3s: ".format(lineNumber) else "     "
                append("    $prefix${it.address}: ${it.code}\n")
            }
        }
        return DexMethod(className, name, type, code)
    }

    private fun readInstructions(): List<DexInstruction> {
        return buildList {
            while (lines.hasNext()) {
                val line = lines.next()
                if (line.startsWith(" ")) {
                    break
                }
                val (address, code) = line.substringAfter('|').split(": ", limit = 2)
                add(DexInstruction(address, code))
            }
        }
    }

    private fun readPositions(): Map<String, Int> {
        return buildMap {
            while (lines.hasNext()) {
                val line = lines.next()
                val match = PositionRegex.matchEntire(line) ?: break
                put(match.getValue("address"), match.getValue("line").toInt())
            }
        }
    }

    private class DexClass(val name: String, val methods: List<DexMethod>) {
        override fun toString() = "class $name\n${methods.joinToString("\n") { it.toString() }}"
    }

    private class DexMethod(val className: String, val name: String, val type: String, val code: String) {
        override fun toString() = "    $name$type // $className.$name()\n$code"
    }

    private class DexInstruction(val address: String, val code: String)
}

private fun MatchResult.getValue(group: String): String {
    return groups[group]?.value ?: throw IllegalStateException("Value of $group not found in $value")
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
