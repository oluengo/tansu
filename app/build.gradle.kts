// Beta (TANSU_BETA=1, rama beta en CI): otra app, «Tansu Beta» (io.github.oluengo.tansu.beta),
// versión X.Y.Z-beta.N y código versionCode·1000 + N. Se instala junto a la normal, con sus propios datos.
val tansuVersion = "0.3.1"
val tansuCode = 4
val beta = System.getenv("TANSU_BETA") == "1"
val betaN = (System.getenv("TANSU_BETA_N") ?: "0").toInt()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.oluengo.tansu"
    compileSdk = 35

    defaultConfig {
        applicationId = if (beta) "io.github.oluengo.tansu.beta" else "io.github.oluengo.tansu"
        minSdk = 26
        targetSdk = 35
        versionCode = if (beta) tansuCode * 1000 + (betaN % 1000) else tansuCode
        versionName = if (beta) "$tansuVersion-beta.$betaN" else tansuVersion
        resValue("string", "app_name", if (beta) "Tansu Beta" else "Tansu")
        buildConfigField("boolean", "BETA", beta.toString())
        // Solo nuestros tres idiomas: evita que las librerías añadan otros al APK.
        resourceConfigurations += listOf("en", "es", "ca")
    }

    signingConfigs {
        // Firma de release desde variables de entorno (GitHub Actions). Si faltan, release queda sin firmar.
        val ksPath = System.getenv("TANSU_KEYSTORE")?.takeIf { it.isNotBlank() }
        if (ksPath != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("TANSU_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TANSU_KEY_ALIAS")
                keyPassword = System.getenv("TANSU_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(project(":core"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
