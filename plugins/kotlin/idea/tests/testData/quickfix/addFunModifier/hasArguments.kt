// "Add 'fun' modifier to 'I'" "false"
// DISABLE_ERRORS
// ACTION: Convert to anonymous object
// ACTION: Introduce import alias
// ACTION: Split property declaration
interface I {
    fun f()
}

fun test() {
    val x = <caret>I(1) {}
}
