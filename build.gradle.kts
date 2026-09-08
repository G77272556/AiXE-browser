// Plik na poziomie projektu (root) — tylko deklaruje wersje pluginów, które moduł app faktycznie
// stosuje w app/build.gradle.kts. Nic się tu nie kompiluje.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.24" apply false
}
