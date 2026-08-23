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

    // 코어(Rust)를 담은 .so 를 여기서 집어 온다. cargo 가 만든 것을 그대로 쓴다.
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    // 지금은 실기(arm64)만 만든다. 다른 ABI 는 필요해질 때 늘린다.
    defaultConfig {
        ndk {
            abiFilters += "arm64-v8a"
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
