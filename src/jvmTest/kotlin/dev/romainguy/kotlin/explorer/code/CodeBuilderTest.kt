package dev.romainguy.kotlin.explorer.code

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CodeBuilderTest {
    @Test
    fun sourceToCodeMapping() {
        val code = buildCode {
            writeInstruction(Instruction(address = 1, code = "m1 1", jumpAddress = -1, lineNumber = 1))
            writeInstruction(Instruction(address = 2, code = "m1 2", jumpAddress = -1, lineNumber = -1))
            writeInstruction(Instruction(address = 3, code = "m1 3", jumpAddress = -1, lineNumber = 2))
            endMethod()
            writeInstruction(Instruction(address = 1, code = "m2 1", jumpAddress = -1, lineNumber = 3))
            writeInstruction(Instruction(address = 2, code = "m2 2", jumpAddress = -1, lineNumber = -1))
            writeInstruction(Instruction(address = 3, code = "m2 3", jumpAddress = -1, lineNumber = 4))
        }.build()

        assertThat(code.text.trimIndent()).isEqualTo(
            """
                1:    m1 1
                      m1 2
                2:    m1 3
                
                3:    m2 1
                      m2 2
                4:    m2 3
            """.trimIndent().trimEnd()
        )
        assertThat(code.getCodeLines(1..4)).containsExactly(0, 2, 4, 6).inOrder()
    }

    @Test
    fun codeToSourceMapping() {
        val code = buildCode {
            writeInstruction(Instruction(address = 1, code = "m1 1", jumpAddress = -1, lineNumber = 1))
            writeInstruction(Instruction(address = 2, code = "m1 2", jumpAddress = -1, lineNumber = -1))
            writeInstruction(Instruction(address = 3, code = "m1 3", jumpAddress = -1, lineNumber = 2))
            endMethod()
            writeInstruction(Instruction(address = 1, code = "m2 1", jumpAddress = -1, lineNumber = 3))
            writeInstruction(Instruction(address = 2, code = "m2 2", jumpAddress = -1, lineNumber = -1))
            writeInstruction(Instruction(address = 3, code = "m2 3", jumpAddress = -1, lineNumber = 4))
        }.build()

        val text = code.text.trimIndent()
        assertThat(text).isEqualTo(
            """
                1:    m1 1
                      m1 2
                2:    m1 3
                
                3:    m2 1
                      m2 2
                4:    m2 3
            """.trimIndent().trimEnd()
        )
        assertThat(code.getCodeLines(1..4)).containsExactly(0, 2, 4, 6).inOrder()
        assertThat(code.getSourceLines(0 until text.lines().size))
            .containsExactly(1, 1, 2, -1, 3, 3, 4)
            .inOrder()
    }
}

private fun Code.getSourceLines(codeLines: Iterable<Int>) = codeLines.map { getSourceLine(it) }

private fun Code.getCodeLines(sourceLines: Iterable<Int>) = sourceLines.map { getCodeLine(it) }