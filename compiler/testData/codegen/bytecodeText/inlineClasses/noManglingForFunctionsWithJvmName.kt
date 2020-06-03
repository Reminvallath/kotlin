// WITH_RUNTIME
inline class IC(val x: Int)

class C {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("test")
    fun test() = IC(42)
}

fun call() = C().test()

// 1 public final test\(\)I
// 1 INVOKEVIRTUAL C.test \(\)I
