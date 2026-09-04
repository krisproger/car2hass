plugins {
    id("com.android.application")
}

import java.util.Properties

// Versioning mirrors build_apk.sh: versionCode comes from build_counter.txt,
// versionName from the VERSION_NAME environment variable; the literal below is
// only a fallback and must stay in sync with the integration manifest.json
// (enforced by tests/test_version_consistency.py).
val buildCounterFile = rootProject.file("build_counter.txt")
val buildNum = if (buildCounterFile.exists()) buildCounterFile.readText().trim().toInt() else 1
val appVersionName = System.getenv("VERSION_NAME") ?: "3.0.31"

// Signing credentials: prefer environment variables / local.properties (used by
// build_apk.sh and release CI), fall back to the dev keystore checked into .keys/.
// The .keys/ directory is git-ignored, so the fallback values below never leak to
// the repository.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String, envName: String, default: String): String {
    val env = System.getenv(envName)
    return localProps.getProperty(name) ?: env ?: default
}
val ksFile = prop("keystoreFile", "KEYSTORE_FILE", ".keys/dev-release.jks")
val ksPassword = prop("keystorePass", "KEYSTORE_PASS", "devpass")
val ksAlias = prop("keyAlias", "KEY_ALIAS", "devkey")
val keyPwd = prop("keyPass", "KEY_PASS", "devpass")

android {
    namespace = "com.car2hass"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.car2hass"
        minSdk = 24
        targetSdk = 35
        versionCode = buildNum
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(ksFile)
            storePassword = ksPassword
            keyAlias = ksAlias
            keyPassword = keyPwd
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Sign debug builds with the same dev key so both variants can be
            // installed side by side with the shell-script builds. In CI the
            // keystore is not checked in — fall back to the auto-generated
            // debug key so assembleDebug keeps working on a fresh checkout.
            val keyFile = rootProject.file(ksFile)
            if (keyFile.isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.fragment:fragment:1.6.2")
}
