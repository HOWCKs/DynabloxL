plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Optional CI signing.
 *
 * When the workflow exports DBX_KEYSTORE_FILE (path to a generated/secret keystore) the release
 * build is signed with it. Locally — or whenever that file is missing — the release build falls
 * back to the debug signing config so `./gradlew assembleRelease` always produces an installable
 * APK and never fails for missing credentials.
 */
val ciKeystoreFile: File? = providers.environmentVariable("DBX_KEYSTORE_FILE")
    .orNull
    ?.takeIf { it.isNotBlank() }
    ?.let { path -> file(path).takeIf { it.exists() } }

val ciVersionCode: Int = providers.environmentVariable("DBX_VERSION_CODE")
    .orNull
    ?.toIntOrNull()
    ?: 1

val ciVersionName: String = providers.environmentVariable("DBX_VERSION_NAME")
    .orNull
    ?.takeIf { it.isNotBlank() }
    ?: "0.1.0"

android {
    namespace = "com.dynablox.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dynablox.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = ciVersionCode
        versionName = ciVersionName
        vectorDrawables.useSupportLibrary = true
        resourceConfigurations += listOf("en", "pt-rBR")
    }

    signingConfigs {
        if (ciKeystoreFile != null) {
            create("ciRelease") {
                storeFile = ciKeystoreFile
                storePassword = providers.environmentVariable("DBX_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("DBX_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("DBX_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
        }
        release {
            // R8 is intentionally left off for the MVP: the app leans on reflection-free
            // third-party code (Shizuku binder stubs, GLES, AccessibilityService) and a stable
            // unobfuscated build is worth far more than a few hundred KB right now.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (ciKeystoreFile != null) {
                signingConfigs.getByName("ciRelease")
            } else {
                signingConfigs.getByName("debug")
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
        viewBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Shizuku: shell-level privileges for the virtual gamepad injection, the real
    // SurfaceFlinger FPS probe and the reversible system tweaks in the optimizer.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
}
