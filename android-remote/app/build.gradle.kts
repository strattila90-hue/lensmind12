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
}
