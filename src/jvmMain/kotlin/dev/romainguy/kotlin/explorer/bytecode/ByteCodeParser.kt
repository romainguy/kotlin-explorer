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

import androidx.collection.IntIntMap
import androidx.collection.mutableIntIntMapOf
import dev.romainguy.kotlin.explorer.PeekingIterator
import dev.romainguy.kotlin.explorer.code.*
import dev.romainguy.kotlin.explorer.code.CodeContent.Error
import dev.romainguy.kotlin.explorer.code.CodeContent.Success
import dev.romainguy.kotlin.explorer.consumeUntil
import dev.romainguy.kotlin.explorer.getValue
import dev.romainguy.kotlin.explorer.oat.codeToOpAndOperands

/*
 * Example:
 * ```
 * public final class KotlinExplorerKt {
 * ```
 */
private val ClassRegex = Regex("^(?<header>.* class [_a-zA-Z][_.$\\w]+).*\\{$")

/**
 * Example:
 * ```
 *   testData.InnerClassKt$main$1();
 * ```
 */
private val MethodRegex = Regex("^ {2}(?<header>.*\\));$")

/**
 * Example:
 *
 * ```
 *     10: ifne          16
 * ```
 */

private val InstructionRegex = Regex("^(?<address>\\d+): +(?<code>.*)$")

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

private val CommentRegex = Regex("\\s+//")

class ByteCodeParser {
    fun parse(text: String): CodeContent {
        return try {
            val lines = PeekingIterator(text.lineSequence().iterator())

            val classes = buildList {
                while (lines.hasNext()) {
                    val match = lines.consumeUntil(ClassRegex) ?: break
                    val clazz = lines.readClass(match.getValue("header"))
                    add(clazz)
                }
            }
            Success(classes)
        } catch (e: Exception) {
            Error(e)
        }
    }

}

private fun PeekingIterator<String>.readClass(classHeader: String): Class {
    val methods = buildList {
        while (hasNext()) {
            val line = peek()
            when {
                line == "}" -> break
                MethodRegex.matches(line) -> add(readMethod())
                else -> next()
            }
        }
        if (next() != "}") {
            throw IllegalStateException("Expected '}' but got '${peek()}'")
        }
    }
    return Class(classHeader, methods, false)
}


private fun PeekingIterator<String>.readMethod(): Method {
    val match = MethodRegex.matchEntire(next())
        ?: throw IllegalStateException("Expected method but got '${peek()}'")
    val header = match.getValue("header")
    if (next().trim() != "Code:") {
        throw IllegalStateException("Expected 'Code:' but got '${peek()}'")
    }
    val instructions = readInstructions()
    val lineNumbers = readLineNumbers()

    return Method(header, InstructionSet(ISA.ByteCode, instructions.withLineNumbers(lineNumbers)))
}


private fun PeekingIterator<String>.readInstructions(): List<Instruction> {
    return buildList {
        while (hasNext()) {
            val line = peek().trim()
            val match = InstructionRegex.matchEntire(line) ?: break
            next()
            val address = match.getValue("address")
            val code = match.getValue("code").replace(CommentRegex, " //")
            val jumpAddress = JumpRegex.matchEntire(code)?.getValue("address")?.toInt() ?: -1
            val (op, operands) = codeToOpAndOperands(code)
            add(Instruction(address.toInt(), address, op, operands, jumpAddress))
        }
    }
}

private fun PeekingIterator<String>.readLineNumbers(): IntIntMap {
    val map = mutableIntIntMapOf()
    val found = skipToLineNumberTable()
    if (!found) {
        return map
    }
    next()
    while (hasNext()) {
        val line = peek().trim()
        if (!line.startsWith("line")) {
            break
        }
        next()
        val (lineNumber, address) = line.substringAfter(' ').split(": ", limit = 2)
        map.put(address.toInt(), lineNumber.toInt())
    }
    return map
}

private fun PeekingIterator<String>.skipToLineNumberTable(): Boolean {
    while (hasNext()) {
        val line = peek().trim()
        when (line) {
            "LineNumberTable:" -> return true
            "", "}" -> return false
        }
        next()
    }
    return false
}
