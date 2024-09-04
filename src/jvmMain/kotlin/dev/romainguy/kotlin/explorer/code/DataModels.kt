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

import androidx.collection.*

enum class ISA(val branchInstructions: ScatterSet<String>, val returnInstructions: ScatterSet<String>) {
    ByteCode(scatterSetOf("if"), scatterSetOf("areturn", "ireturn", "lreturn", "dreturn", "freturn", "return")),
    Dex(scatterSetOf("if"), scatterSetOf("return")),
    Oat(scatterSetOf(), scatterSetOf()),
    X86_64(
        scatterSetOf(
            "je",
            "jz",
            "jne",
            "jnz",
            "js",
            "jns",
            "jg",
            "jnle",
            "jge",
            "jnl",
            "jl",
            "jnge",
            "jle",
            "jng",
            "ja",
            "jnbe",
            "jae",
            "jnb",
            "jb",
            "jnae",
            "jbe",
            "jna"
        ),
        scatterSetOf("ret")
    ),
    Aarch64(
        scatterSetOf(
            "b",
            "b.eq",
            "b.ne",
            "b.cs",
            "b.hs",
            "b.cc",
            "b.lo",
            "b.mi",
            "b.pl",
            "b.vs",
            "b.vc",
            "b.hi",
            "b.ls",
            "b.ge",
            "b.lt",
            "b.gt",
            "b.le",
            "b.al",
            "bl",
            "cbz",
            "cbnz",
            "tbz",
            "tbnz"
        ),
        scatterSetOf("ret")
    )
}

data class Class(val header: String, val methods: List<Method>)

data class Method(val header: String, val instructionSet: InstructionSet, val index: Int = -1)

data class InstructionSet(
    val isa: ISA,
    val instructions: List<Instruction>,
    val methodReferences: IntObjectMap<MethodReference> = emptyIntObjectMap()
)

data class Instruction(
    val address: Int,
    val label: String,
    val op: String,
    val operands: String,
    val jumpAddress: Int,
    val callAddress: Int = -1,
    val callAddressMethod: Int = -1,
    val lineNumber: Int = -1
)

data class MethodReference(val address: Int, val name: String)

fun List<Instruction>.withLineNumbers(lineNumbers: IntIntMap): List<Instruction> {
    return map { it.copy(lineNumber = lineNumbers.getOrDefault(it.address, -1)) }
}
