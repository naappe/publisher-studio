plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.lanlens"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.lanlens"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
