// CONFIGURE_LIBRARY: JUnit
// NO_TEMPLATE_TESTING
import org.junit.Test

class A {
    @Test fun <caret>`testTwo  +  Two==Four`() {}
}

fun test() {
    A().`testTwo  +  Two==Four`()
}