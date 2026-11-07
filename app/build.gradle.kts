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
        versionCode = 413
        versionName = "4.1.3"
    }

    signingConfigs {
        create("release") {
            val keyStorePath = System.getenv("MX_KEYSTORE")
            if (!keyStorePath.isNullOrBlank()) {
                storeFile = file(keyStorePath)
                storePassword = System.getenv("MX_STORE_PASS").orEmpty()
                keyAlias = System.getenv("MX_KEY_ALIAS").orEmpty()
                keyPassword = System.getenv("MX_KEY_PASS").orEmpty()
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
        }
        release {
            if (!System.getenv("MX_KEYSTORE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}
