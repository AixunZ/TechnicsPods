buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 owns Kotlin; upgrade its compiler to the version required by Miuix.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
