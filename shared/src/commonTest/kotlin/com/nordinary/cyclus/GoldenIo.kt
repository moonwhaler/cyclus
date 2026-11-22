package com.nordinary.cyclus

/** Reads a golden JSON file from `shared/golden/` (packaged as a test resource on JVM and Android). */
expect fun readGolden(name: String): String
