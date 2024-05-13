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

import com.google.common.truth.Truth.assertThat
import dev.romainguy.kotlin.explorer.testing.Builder
import dev.romainguy.kotlin.explorer.testing.parseSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ByteCodeParserTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val builder by lazy { Builder.getInstance(temporaryFolder.root.toPath()) }
    private val byteCodeParser = ByteCodeParser()

    @Test
    fun issue_45() {
        val content = byteCodeParser.parseSuccess(builder.generateByteCode("Issue_45.kt"))

        assertThat(content.classes.map { it.header }).containsExactly(
            "final class testData.Issue_45Kt\$main$1",
            "public final class testData.Issue_45Kt",
        )
        assertThat(content.classes.flatMap { it.methods }.map { it.header }).containsExactly(
            "testData.Issue_45Kt\$main\$1()",
            "public final void invoke()",
            "public java.lang.Object invoke()",
            "public static final void main()",
            "public static final void f1(kotlin.jvm.functions.Function0<kotlin.Unit>)",
            "public static final void f2()",
            "public static void main(java.lang.String[])",
        )
    }
}
