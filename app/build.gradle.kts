plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.fotopotok.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fotopotok.app"
        minSdk = 26
        targetSdk = 34
        // CI passes a strictly increasing number (workflow run count); fall back to a
        // static value for local builds. The app's auto-update compares this against
        // the latest GitHub release, so it must go UP on every published build.
        val buildCode = project.findProperty("vdVersionCode") as String?
        versionCode = buildCode?.toIntOrNull() ?: 2
        versionName = "1.0.1"

        // The server address for the photo feed / chat. Leave empty to open the
        // app in "set up server" mode on first launch (the address can be entered
        // in the app and is stored on the device).
        buildConfigField("String", "DEFAULT_API_BASE_URL", "\"https://app-1a4df5ab7ae2.vibecode.bitrix24.tech\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil)
}
