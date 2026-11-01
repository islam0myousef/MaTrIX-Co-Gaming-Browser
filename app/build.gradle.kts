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
    versionCode = 12
    versionName = "3.5.0"
}

signingConfigs {
    create("release") {
        val ks = System.getenv("MX_KEYSTORE")
        if (!ks.isNullOrEmpty() && file(ks).exists()) {
            storeFile = file(ks)
            storePassword = System.getenv("MX_STORE_PASS")
            keyAlias = System.getenv("MX_KEY_ALIAS")
            keyPassword = System.getenv("MX_KEY_PASS")
        }
    }
}

buildTypes {
    release {
        isMinifyEnabled = false
        if (!System.getenv("MX_KEYSTORE").isNullOrEmpty()) signingConfig = signingConfigs.getByName("release")
    }
}

lint {
    checkReleaseBuilds = false
    abortOnError = false
}

compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlinOptions {
    jvmTarget = "17"
}

}
