plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.devfive.hanbeon"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.devfive.hanbeon"
        // 오버레이(TYPE_APPLICATION_OVERLAY)가 API 26부터라 그 아래는 받지 않는다.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
