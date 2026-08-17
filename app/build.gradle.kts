plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.castdlna.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.castdlna.tv"
        // 24 lo marca Wulfenite, que es de donde sale la interfaz. Android TV
        // arrancó en 21, pero el parque real de televisores está muy por
        // encima y no compensa renunciar a la librería por cubrirlo.
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    sourceSets["main"].java.srcDirs("src/main/kotlin")

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    // Wulfenite se trae como artefacto: usa su propio catálogo de versiones y
    // compartirlo obligaría a tocar su configuración. `actualizar-wulfenite.sh`
    // rehace el AAR cuando la librería cambie.
    implementation(files("libs/wulf-compose-release.aar"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Media3 trae el control de velocidad para directos de fábrica, que es
    // justo lo que en el receptor de escritorio hubo que dejar sin resolver.
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.0")

    testImplementation("junit:junit:4.13.2")
}
