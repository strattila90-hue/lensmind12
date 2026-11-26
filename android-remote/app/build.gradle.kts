plugins {
    id("com.android.application")
}

// A GitHub Actions bot adja át (lásd .github/workflows/build-onda.yml)
val buildNum = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
val repoSlug = (project.findProperty("repoSlug") as String?) ?: "strattila90-hue/lensmind12"

android {
    namespace = "hu.onda"
    compileSdk = 34

    defaultConfig {
        applicationId = "hu.onda.haptic"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNum
        versionName = "onda-$buildNum"
        buildConfigField("int", "BUILD_NUMBER", "$buildNum")
        buildConfigField("String", "REPO", "\"$repoSlug\"")
    }

    // Állandó aláírókulcs, hogy a frissítés minden buildnél ráférjen az előzőre.
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
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    // egységes Kotlin-futtatókönyvtár (a CameraX/ML Kit régi jdk7/jdk8 darabjai ütköznének)
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
    implementation("androidx.activity:activity:1.9.0")
    // Wi-Fi: LG webOS WebSocket (szöveg beírása a TV-be)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // Offline képfelismerés a TV azonosításához (mint a régi Lens Mind)
    implementation("com.google.mlkit:image-labeling:17.0.9")
}
