import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersion = rootProject.layout.projectDirectory.file("VERSION").asFile.readText().trim()

// Release key lives outside the repo; absent (CI, fresh clone) -> release falls back to the debug key.
val signingProps = Properties().apply {
    val f = File(System.getProperty("user.home"), ".config/app-depot-signing/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = signingProps.getProperty("storePassword") != null

android {
    namespace = "com.vuhpquang.appdepot"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vuhpquang.appdepot"
        minSdk = 34
        targetSdk = 36
        // 0.1.0 -> 100, 1.2.3 -> 10203 (same as core/VersionCode.kt)
        versionCode = appVersion.also { require(Regex("""\d+\.\d+\.\d+""").matches(it)) { "VERSION must be X.Y.Z, was '$it'" } }
            .split(".").map { it.toInt() }.let { (a, b, c) -> a * 10_000 + b * 100 + c }
        versionName = appVersion
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = File(System.getProperty("user.home"), ".config/app-depot-signing/app-depot-release.jks")
                storePassword = signingProps.getProperty("storePassword")
                keyPassword = signingProps.getProperty("keyPassword")
                keyAlias = signingProps.getProperty("keyAlias", "appdepot")
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".dev" }
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("WARNING: ~/.config/app-depot-signing/keystore.properties not found; release is signed with the debug key.")
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
