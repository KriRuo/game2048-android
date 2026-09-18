import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is intentionally NOT stored in this repo. Drop a keystore.properties
// file (gitignored) next to this build file with: storeFile, storePassword, keyAlias,
// keyPassword. Without it, the release build type is simply left unsigned (fine for
// local `assembleRelease` testing) -- see the release buildType below.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Firebase Analytics/Crashlytics config is intentionally NOT stored in this repo, and this
// project deliberately skips the google-services Gradle plugin (which would require committing
// a google-services.json). Drop a firebase.properties file (gitignored) next to this build file
// with: apiKey, applicationId (the Firebase *App* ID, e.g. "1:123:android:abc" -- not this
// module's Android applicationId above), projectId -- all read from a Firebase project's
// Project Settings > General > Your apps. Without it, FIREBASE_ENABLED is false and
// AppAnalytics.init() no-ops entirely; see analytics/AppAnalytics.kt.
val firebasePropertiesFile = rootProject.file("firebase.properties")
val firebaseProperties = Properties().apply {
    if (firebasePropertiesFile.exists()) {
        firebasePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.game2048"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kriruo.game2048"
        minSdk = 24
        targetSdk = 35
        // Overridable via -PversionCode=<n> (release-build.yml passes the CI run number so
        // every uploaded bundle gets a strictly increasing versionCode, as Play requires).
        // Local builds without that property default to 1.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "FIREBASE_ENABLED", firebasePropertiesFile.exists().toString())
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseProperties.getProperty("apiKey", "")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseProperties.getProperty("applicationId", "")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseProperties.getProperty("projectId", "")}\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Always present so AppAnalytics.kt compiles regardless of whether firebase.properties
    // exists -- initialization itself is config-gated at runtime (FIREBASE_ENABLED above), and
    // the google-services plugin (which would need a committed google-services.json) is
    // deliberately not used at all; see AppAnalytics.kt and AndroidManifest.xml.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
