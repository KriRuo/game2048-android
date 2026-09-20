import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // google-services is NOT listed here -- see the `apply(plugin = ...)` call below. The
    // `plugins {}` block is evaluated in an isolated scope, statically, before the rest of this
    // script runs, and can't contain conditional logic referencing any external class (not even
    // java.io.File) -- only plugin id()/version()/apply-false literals.
}

// Firebase (Analytics/Crashlytics/Auth/Firestore) needs the google-services plugin applied,
// which in turn needs app/google-services.json (gitignored -- fetched via
// `firebase apps:sdkconfig ANDROID <APP_ID> --project <PROJECT_ID>`, never committed). Without
// it, the plugin is simply not applied and Firebase is left uninitialized -- every call in
// AppAnalytics.kt/AuthRepository.kt/CloudSyncRepository.kt is defensively guarded for that case,
// so a fresh clone or CI (neither of which has this file) still builds and runs normally. This
// `apply(plugin = ...)` (old-style, dynamic) form is used instead of listing it in `plugins {}`
// above precisely because it needs to be conditional -- see the comment there. The plugin
// itself is registered (version pinned, not yet applied) via `apply false` in the root
// build.gradle.kts, which is what makes the bare id below resolvable here.
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
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

        // So app code can tell whether Firebase is actually configured in this build without
        // reaching into Gradle internals -- see AppAnalytics.kt/AuthRepository.kt.
        buildConfigField("boolean", "FIREBASE_ENABLED", googleServicesFile.exists().toString())
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
        // Firestore/Auth's gRPC transport touches java.time classes that only exist natively on
        // API 26+; minSdk here is 24, so without desugaring, loading those classes on an
        // API 24/25 device throws immediately (NoClassDefFoundError on java.time.*) the moment
        // Firebase code runs -- a plausible cause of a crash that only shows up with Firebase
        // present and that no try/catch of ours would have a chance to run before.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Robolectric (see the testImplementation below) needs the merged manifest/resources
    // available to unit tests -- without this it can't resolve the app's package/resources and
    // fails constructing a real Application/Context.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Always present so Firebase-using code compiles regardless of whether
    // google-services.json exists -- see the comment on googleServicesFile above.
    // Note: NOT the latest BoM (34.19.0 as of this writing) -- its firebase-auth/
    // play-services-measurement artifacts are compiled against Kotlin 2.2/2.3 metadata, which
    // this project's Kotlin plugin (pinned to 2.0.21 in the root build.gradle.kts, alongside
    // AGP/Compose-compiler versions that pair with it) can't read. 33.5.1 is the newest BoM
    // verified to compile cleanly against that pin.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    testImplementation("junit:junit:4.13.2")
    // Lets GameViewModel (an AndroidViewModel needing a real Application/SharedPreferences) run
    // as a plain JVM unit test instead of needing a device/emulator -- see GameViewModelTest.
    // Pinned to a version with confirmed API 34 support; tests target that via @Config(sdk = [34])
    // rather than compileSdk's 35, sidestepping any framework-jar lag for a brand-new API level.
    testImplementation("org.robolectric:robolectric:4.13")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
