// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'
// WITH_STDLIB

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}