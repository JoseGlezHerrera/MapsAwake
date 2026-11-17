plugins {
    id("com.android.application")
}

android {
    namespace = "dev.mapsawake"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.mapsawake"
        minSdk = 30 // Wear OS 3
        targetSdk = 36 // Galaxy Watch7 con One UI Watch / Wear OS 6 (API 36)
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // R8 deja el APK en una fracción del tamaño (las librerías de la
            // tile pesan). Se firma con la clave debug local (~/.android/
            // debug.keystore): para uso personal basta, pero para actualizar
            // hay que firmar siempre con la misma clave.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Tile ON/OFF
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.concurrent:concurrent-futures:1.3.0")
}
