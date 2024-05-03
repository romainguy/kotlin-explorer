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

data class Class(val header: String, val methods: List<Method>)

data class Method(val header: String, val instructions: List<Instruction>)

data class Instruction(val address: Int, val code: String, val jumpAddress: Int?, val lineNumber: Int? = null)

fun List<Instruction>.withLineNumbers(lineNumbers: Map<Int, Int>): List<Instruction> {
    return map { it.copy(lineNumber = lineNumbers[it.address]) }

}