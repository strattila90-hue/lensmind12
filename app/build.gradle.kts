plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A GitHub Actions bot ezeket adja át (lásd .github/workflows/build.yml)
val buildNum = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
val repoSlug = (project.findProperty("repoSlug") as String?) ?: "strattila90-hue/lensmind"

android {
    namespace = "hu.lensmind"
    compileSdk = 34

    defaultConfig {
        applicationId = "hu.lensmind"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNum
        versionName = "build-$buildNum"
        buildConfigField("int", "BUILD_NUMBER", "$buildNum")
        buildConfigField("String", "REPO", "\"$repoSlug\"")
    }

    // Állandó aláírókulcs: így a frissítés minden buildnél "ráfér" az előzőre.
    signingConfigs {
        create("shared") {
            storeFile = file("lensmind.keystore")
            storePassword = "lensmind"
            keyAlias = "lensmind"
            keyPassword = "lensmind"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("shared") }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // On-device (offline) képfelismerés + QR olvasás
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // QR generálás
    implementation("com.google.zxing:core:3.5.3")
}
