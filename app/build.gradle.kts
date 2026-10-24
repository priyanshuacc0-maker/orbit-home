import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional stable signing key: read from keystore.properties when present (never
// committed). Without it, Android uses the default debug key.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasStableKeystore = keystorePropsFile.exists()

// Minify + shrink the release build (the one to use day to day). On by default;
// can be turned off for constrained build environments with -Pminify=false.
val releaseMinify = (project.findProperty("minify") as String?)?.toBoolean() ?: true

android {
    namespace = "com.orbit.home"
    // API 36: Play requires 36 or higher for submissions and updates as of
    // 14 October 2026 (remediation 6.23). compileSdk and targetSdk move together,
    // and the behaviour changes that come with a targetSdk bump were reviewed
    // rather than the number simply being changed.
    compileSdk = 36

    signingConfigs {
        if (hasStableKeystore) {
            create("stable") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.orbit.home"
        minSdk = 26
        targetSdk = 36
        versionCode = 244
        versionName = "1.167.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            // Debug Compose builds are much slower, so the release build is the
            // one to use day to day.
            isMinifyEnabled = releaseMinify
            isShrinkResources = releaseMinify
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with the stable key when present, otherwise the debug key so
            // the release APK is still installable.
            signingConfig = if (hasStableKeystore) {
                signingConfigs.getByName("stable")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // Sign debug builds with the stable key too, so a new build installs
            // over the previous one instead of demanding a reinstall.
            if (hasStableKeystore) signingConfig = signingConfigs.getByName("stable")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // The centre's video. ExoPlayer streams through the hardware decoder into a surface, so no
    // frame is ever decoded by hand — which is the difference between smooth and choppy.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Ships a baseline profile so startup and transitions are smoother.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
