plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val releaseStoreFile = System.getenv("TIMITRAIN_STORE_FILE")?.takeIf { it.isNotBlank() }
val releaseStorePassword = System.getenv("TIMITRAIN_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = System.getenv("TIMITRAIN_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = System.getenv("TIMITRAIN_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseSigningReady = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

android {
    namespace = "de.timitrain"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.timitrain"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("TIMITRAIN_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("TIMITRAIN_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.reorderable)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    debugImplementation(libs.compose.ui.tooling)
}
