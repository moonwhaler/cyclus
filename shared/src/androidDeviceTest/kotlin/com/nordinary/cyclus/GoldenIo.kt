package com.nordinary.cyclus

actual fun readGolden(name: String): String {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(name)
        ?: object {}.javaClass.classLoader!!.getResourceAsStream(name)
        ?: error("golden resource $name not found")
    return stream.use { it.readBytes().decodeToString() }
}
