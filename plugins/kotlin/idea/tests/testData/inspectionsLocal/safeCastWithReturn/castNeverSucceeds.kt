// PROBLEM: none
fun test(x: Int) {
    <caret>x as? String ?: return
}
// IGNORE_K2