plugins {
    id("com.android.application")
}

android {
    namespace = "com.marshall.stockai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.marshall.stockai"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
