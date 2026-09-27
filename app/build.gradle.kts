plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "org.communitypoke.opencomputers"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.communitypoke.opencomputers"
        minSdk = 24
        targetSdk = 34
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":emulator-core"))
}
