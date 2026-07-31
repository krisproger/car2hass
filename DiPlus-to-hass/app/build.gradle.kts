plugins {
    id("com.android.application")
}

// Versioning mirrors build_apk.sh: versionCode comes from build_counter.txt,
// versionName from the VERSION_NAME environment variable (default 1.9.5).
val buildCounterFile = rootProject.file("build_counter.txt")
val buildNum = if (buildCounterFile.exists()) buildCounterFile.readText().trim().toInt() else 1
val appVersionName = System.getenv("VERSION_NAME") ?: "2.1.3"

android {
    namespace = "com.diplustohass"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.diplustohass"
        minSdk = 24
        targetSdk = 35
        versionCode = buildNum
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(".keys/dev-release.jks")
            storePassword = "devpass"
            keyAlias = "devkey"
            keyPassword = "devpass"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            // Sign debug builds with the same dev key so both variants can be
            // installed side by side with the shell-script builds.
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            res.srcDirs("src/main/res")
            assets.srcDirs("src/main/assets")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
        // Unit tests are plain main()-style classes executed by
        // run_java_tests.sh, not JUnit — do not map a test source set here.
    }
}

// The app uses the Android framework, vendored adblib sources, and osmdroid
// (OpenStreetMap) for the geofence map editor.
dependencies {
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}
