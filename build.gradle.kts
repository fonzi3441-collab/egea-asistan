// Sürümler: AGP 9.x yerleşik Kotlin desteğiyle gelir (app modülünde ayrıca kotlin-android eklentisi yok).
// Kotlin JVM eklentisi saf Kotlin "core" modülü için; buradaki sürüm AGP'nin kullandığı KGP'yi de belirler.
plugins {
    id("com.android.application") version "9.5.2" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
}
