plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.technics.pods"
    buildToolsVersion = "36.1.0"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.technics.pods"
        minSdk = 34
        targetSdk = 36
        versionCode = 30
        versionName = "0.10.0-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseStore = providers.environmentVariable("TECHNICSPODS_KEYSTORE").orNull
    val releaseStorePassword = providers.environmentVariable("TECHNICSPODS_STORE_PASSWORD").orNull
    val releaseAlias = providers.environmentVariable("TECHNICSPODS_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("TECHNICSPODS_KEY_PASSWORD").orNull
    if (releaseStore != null) {
        require(!releaseStorePassword.isNullOrEmpty() && !releaseAlias.isNullOrEmpty()
                && !releaseKeyPassword.isNullOrEmpty()) { "Release signing environment is incomplete" }
        signingConfigs.create("maintainer") {
            storeFile = file(releaseStore)
            storePassword = releaseStorePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            if (releaseStore != null) signingConfig = signingConfigs.getByName("maintainer")
            isMinifyEnabled = true
            isShrinkResources = true
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

    packaging {
        resources.excludes += setOf(
            "/META-INF/AL2.0",
            "/META-INF/LGPL2.1",
            "/META-INF/*.version",
        )
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
