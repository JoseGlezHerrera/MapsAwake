import java.util.Properties

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

    // Clave de las releases publicadas: fuera del repo, en
    // ~/.config/mapsawake/keystore.properties (o la ruta de la propiedad
    // Gradle mapsawake.keystore). Si no existe, se firma con la clave debug
    // local y el APK no se podrá instalar encima de uno de las releases.
    val releaseProps = File(
        providers.gradleProperty("mapsawake.keystore").orNull
            ?: "${System.getProperty("user.home")}/.config/mapsawake/keystore.properties"
    )
    if (releaseProps.isFile) {
        val p = Properties().apply { releaseProps.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = File(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // R8 deja el APK en una fracción del tamaño (las librerías de la
            // tile pesan).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
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
