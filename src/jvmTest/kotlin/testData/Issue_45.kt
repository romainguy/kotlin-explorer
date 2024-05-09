package testData

fun main() {
    f1() {
        f2()
    }
}

fun f1(f: () -> Unit) { f() }
fun f2() {
    println("Hi")
}
