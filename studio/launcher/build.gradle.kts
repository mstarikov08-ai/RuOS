plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ruos.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ruos.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 10000
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    kotlin {
        jvmToolchain(17)
    }

    // Point at the AOSP-tree source — no duplication
    sourceSets {
        getByName("main") {
            kotlin.srcDirs("../../packages/apps/RuOSLauncher/src")
            res.srcDirs("../../packages/apps/RuOSLauncher/res")
            manifest.srcFile("../../packages/apps/RuOSLauncher/AndroidManifest.xml")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines)
}
