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

package dev.romainguy.kotlin.explorer.oat

import dev.romainguy.kotlin.explorer.CodeTextArea
import dev.romainguy.kotlin.explorer.ExplorerState
import dev.romainguy.kotlin.explorer.jump.CompoundJumpDetector
import dev.romainguy.kotlin.explorer.jump.RegexJumpDetector

// Arm64 syntax: '        0x00001040: b.ge #+0x48 (addr 0x1088)'
private val Arm64JumpRegex =
    Regex("^ +0x[0-9a-fA-F]{8}: .+ #(?<direction>[+-])0x[0-9a-fA-F]+ \\(addr 0x(?<address>[0-9a-fA-F]+)\\)$")

// X86 syntax: '        0x00001048: jnl/ge +103 (0x000010b5)'
private val X86JumpRegex =
    Regex("^ +0x[0-9a-fA-F]{8}: .+ (?<direction>[+-])\\d+ \\(0x(?<address>[0-9a-fA-F]{8})\\)$")

private val OatAddressedRegex = Regex("^ +0x(?<address>[0-9a-fA-F]{8}): .+$")

class OatTextArea(explorerState: ExplorerState) :
    CodeTextArea(
        explorerState,
        CompoundJumpDetector(
            RegexJumpDetector(Arm64JumpRegex, OatAddressedRegex),
            RegexJumpDetector(X86JumpRegex, OatAddressedRegex)
        ),
        lineNumberRegex = null
    )

fun main() {
    println(X86JumpRegex.matchEntire("        0x00001048: jnl/ge +103 (0x000010b5)"))
}