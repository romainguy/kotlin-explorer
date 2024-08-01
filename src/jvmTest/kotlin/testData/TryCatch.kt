package testData

fun main() {
    val a = null
    try {
        foo()
    }
    catch (e: Exception) {
        println()
    }
}

fun foo() {
    println()
}
