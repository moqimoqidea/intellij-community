// "Let 'String' implement interface 'A'" "false"
// ACTION: Change parameter 'a' type of function 'foo' to 'String'
// ACTION: Convert to 'buildString' call
// ACTION: Convert to raw string literal
// ACTION: Create function 'foo'
// ERROR: Type mismatch: inferred type is String but A was expected
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'String', but 'A' was expected.

package let.implement

fun bar() {
    foo("Hello"<caret>)
}


fun foo(a: A) {
}

interface A
