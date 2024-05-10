package dev.romainguy.kotlin.explorer.code

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory

class SyntaxStyle private constructor() {
    companion object {
        val Dex: String get() = "text/dex-bytecode"
        val Oat: String get() = "text/oat-assembly"
        val ByteCode: String get() = "text/java-bytecode"

        init {
            val factory = TokenMakerFactory.getDefaultInstance() as AbstractTokenMakerFactory
            factory.putMapping(Dex, DexTokenMarker::class.java.canonicalName)
        }
    }
}
