// "Remove function body" "true"
abstract class A() {
    <caret>abstract fun foo() /*1*/ { // 2
        // 3
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix