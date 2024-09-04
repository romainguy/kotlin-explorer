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

@file:OptIn(ExperimentalJewelApi::class)

package dev.romainguy.kotlin.explorer.code

import androidx.collection.mutableScatterMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

private val Conditions = mutableScatterMapOf(
    "eq" to "equal.",
    "ne" to "not equal.",
    "cs" to "carry set.",
    "cc" to "carry clear.",
    "mi" to "negative.",
    "pl" to "positive or zero.",
    "vs" to "overflow.",
    "vc" to "no overflow.",
    "hi" to "unsigned higher.",
    "ls" to "unsigned lower or same.",
    "ge" to "signed greater than or equal.",
    "lt" to "signed less than.",
    "gt" to "signed greater than.",
    "le" to "signed less than or equal."
)

suspend fun MarkdownProcessor.generateInlineDocumentation(code: Code, line: Int): List<MarkdownBlock> {
    if (code.isa == ISA.Aarch64) {
        val fullOp = code.instructions[line]?.op ?: ""
        val op = fullOp.substringBefore('.')
        val opDocumentation = Aarch64Docs[op]
        if (opDocumentation != null) {
            return withContext(Dispatchers.Default) {
                val blocks = ArrayList<MarkdownBlock>()
                blocks += MarkdownBlock.Heading(2, InlineMarkdown.Text(opDocumentation.name))

                val condition = fullOp.substringAfter('.', "")
                if (condition.isNotEmpty()) {
                    val conditionDocumentation = Conditions[condition]
                    if (conditionDocumentation != null) {
                        blocks += MarkdownBlock.Paragraph(
                            InlineMarkdown.StrongEmphasis("**", InlineMarkdown.Text("Condition: ")),
                            InlineMarkdown.Text(conditionDocumentation)
                        )
                    }
                }

                blocks += processMarkdownDocument(opDocumentation.documentation)

                blocks += MarkdownBlock.Paragraph(
                    InlineMarkdown.Link(
                        opDocumentation.url,
                        "See full documentation",
                        InlineMarkdown.Text("See full documentation")
                    )
                )

                blocks
            }
        }
    }
    return emptyList()
}
