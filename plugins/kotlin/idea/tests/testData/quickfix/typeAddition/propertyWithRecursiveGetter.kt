// "Specify type explicitly" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// ERROR: Type checking has run into a recursive problem. Easiest workaround: specify types of your declarations explicitly
// ACTION: Convert member to extension
// ACTION: Convert property to function
// ACTION: Move to companion object
// ACTION: Specify type explicitly

class A {
    val a<caret>
        get() = a
}
// IGNORE_K2

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention