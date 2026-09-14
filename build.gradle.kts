// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin 2.0+ moved the Compose compiler out of AGP's `composeOptions` and into this
    // dedicated Gradle plugin (must match the Kotlin version above).
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
