import kotlin.reflect.KProperty

class Delegate {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) = this
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = "OK"
}

interface TestInterface {
    companion object {
        val test by Delegate()
    }
}

fun box(): String {
    return TestInterface.test
}