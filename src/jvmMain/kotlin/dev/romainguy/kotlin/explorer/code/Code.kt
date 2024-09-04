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

import androidx.collection.IntIntMap
import androidx.collection.IntObjectMap
import androidx.collection.mutableIntObjectMapOf

/**
 * A data model representing disassembled code
 *
 * Given a list [Class]'s constructs a mode that provides:
 * - Disassembled text with optional line number annotations
 * - Jump information for branch instructions
 */
class Code(
    val isa: ISA,
    val text: String,
    val instructions: IntObjectMap<Instruction>,
    private val jumps: IntIntMap,
    private val sourceToCodeLine: IntIntMap,
    private val codeToSourceToLine: IntIntMap,
) {
    fun getJumpTargetOfLine(line: Int) = jumps.getOrDefault(line, -1)

    fun getCodeLine(sourceLine: Int) = sourceToCodeLine.getOrDefault(sourceLine, -1)

    fun getSourceLine(codeLine: Int) = codeToSourceToLine.getOrDefault(codeLine, -1)

    companion object {
        fun fromClasses(classes: List<Class>, codeStyle: CodeStyle = CodeStyle()): Code {
            return buildCode(codeStyle) {
                val indexedMethods = buildIndexedMethods(classes)
                classes.forEachIndexed { classIndex, clazz ->
                    startClass(clazz)
                    val notLastClass = classIndex < classes.size - 1
                    clazz.methods.forEachIndexed { methodIndex, method ->
                        writeMethod(method, indexedMethods)
                        if (methodIndex < clazz.methods.size - 1 || notLastClass) writeLine("")
                    }
                }
            }.build()
        }

        private fun buildIndexedMethods(classes: List<Class>): IntObjectMap<Method> {
            val map = mutableIntObjectMapOf<Method>()
            classes.forEach { clazz ->
                clazz.methods.forEach { method ->
                    if (method.index != -1) {
                        map[method.index] = method
                    }
                }
            }
            return map
        }
    }
}
