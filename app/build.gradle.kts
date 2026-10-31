plugins {
id("com.android.application")
id("org.jetbrains.kotlin.android")
}

android {
namespace = "com.matrixco.browser"
compileSdk = 35

defaultConfig {
    applicationId = "com.matrixco.browser"
    minSdk = 23
    targetSdk = 28
    versionCode = 11
    versionName = "3.4.0"
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
