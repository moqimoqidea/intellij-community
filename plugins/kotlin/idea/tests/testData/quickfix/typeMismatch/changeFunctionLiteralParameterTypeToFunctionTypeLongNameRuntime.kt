// "Change type from 'String' to '(LinkedHashSet<Int>) -> HashSet<Int>'" "true"

fun foo(f: ((java.util.LinkedHashSet<Int>) -> java.util.HashSet<Int>) -> String) {
    foo {
        f: String<caret> -> "42"
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeTypeFix
// IGNORE_K2