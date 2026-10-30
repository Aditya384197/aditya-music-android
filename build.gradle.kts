buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Keep the Kotlin Gradle plugin aligned with the Compose compiler plugin on AGP 9.x.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
