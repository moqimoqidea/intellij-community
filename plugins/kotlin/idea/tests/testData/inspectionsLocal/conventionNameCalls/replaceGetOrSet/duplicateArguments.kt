// PROBLEM: none
// K2_ERROR: Argument already passed for this parameter.
// K2_ERROR: No value passed for parameter 'b'.
// ERROR: An argument is already passed for this parameter
// ERROR: No value passed for parameter 'b'
fun test() {
    class Test{
        operator fun get(a: Int, b: Int) : Int = 0
    }
    val test = Test()
    test.g<caret>et(a=0, a=1)
}
