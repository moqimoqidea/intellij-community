// "Remove '.java'" "true"
// PRIORITY: HIGH
// WITH_STDLIB
fun foo() {
    val clazz: kotlin.reflect.KClass<Foo> = Foo::class.java<caret>
}

class Foo
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertClassToKClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertClassToKClassFix