plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ruos.settings"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ruos.settings"
        minSdk = 26
        targetSdk = 34
        versionCode = 10000
        versionName = "1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug { applicationIdSuffix = ".debug" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    kotlin {
        jvmToolchain(17)
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("../../packages/apps/RuOSSettings/src")
            res.srcDirs("../../packages/apps/RuOSSettings/res")
            manifest.srcFile("../../packages/apps/RuOSSettings/AndroidManifest.xml")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime)
}
