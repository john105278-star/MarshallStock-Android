plugins {
    id("com.android.application")
}

android {
    namespace = "com.marshall.stockai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.marshall.stockai.v13"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "1.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
