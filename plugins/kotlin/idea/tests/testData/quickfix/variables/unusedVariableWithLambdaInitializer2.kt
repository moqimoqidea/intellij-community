// "Remove variable 'a'" "true"
fun test() {
    val <caret>a: (String) -> Unit = { s: String -> s + s }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix$RemoveVariableFactory$doCreateQuickFix$removePropertyFix$1
// IGNORE_K2
// Task for K2: KTIJ-29591