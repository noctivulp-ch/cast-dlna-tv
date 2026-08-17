plugins {
    id("com.android.application") version "8.7.3" apply false
    // La versión la marca Wulfenite: un AAR compilado con Kotlin 2.2 no se
    // puede leer desde un compilador anterior.
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
