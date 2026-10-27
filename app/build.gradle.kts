plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.thangmaytaman.crm"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.thangmaytaman.crm"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        val url = (project.findProperty("CRM_URL") as String?) ?: "https://kh.thangmaytaman.vn/"
        buildConfigField("String", "CRM_URL", "\"$url\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
