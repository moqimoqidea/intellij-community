// PRIORITY: HIGH
// INTENTION_TEXT: "Import members from 'java.util.regex.Pattern'"
// WITH_STDLIB
// ERROR: Unresolved reference: unresolved
// AFTER-WARNING: Variable 'field' is never used
// AFTER-WARNING: Variable 'fieldFqn' is never used
// K2_ERROR: Unresolved reference 'unresolved'.
// K2_AFTER_ERROR: Unresolved reference 'unresolved'.

import java.util.regex.Pattern

fun foo() {
    Pattern.matches("", "")

    val field = <caret>Pattern.CASE_INSENSITIVE

    Pattern.compile("")

    val fieldFqn = java.util.regex.Pattern.CASE_INSENSITIVE

    Pattern.unresolved
}
