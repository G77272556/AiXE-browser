plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt") // wymagane przez Room (generowanie kodu z adnotacji @Entity/@Dao/@Database)
}

android {
    namespace = "com.superbrowser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.superbrowser"
        // minSdk = 26 (Android 8.0): WebViewHardening woła settings.safeBrowsingEnabled bez osobnego
        // sprawdzania wersji (ta właściwość WebView istnieje dopiero od API 26) — 26 to bezpieczne
        // minimum przy którym cała reszta kodu (SAF, DataStore, Compose) też działa bez ograniczeń.
        // Android 10 to API 29, czyli mieści się w tym zakresie z zapasem.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        // Musi być zgodne z wersją Kotlina zadeklarowaną w root build.gradle.kts (1.9.24).
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            // Standardowe konflikty plików licencyjnych z bibliotek Kotlin/Coroutines w META-INF.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ----- Podstawy Androida / Kotlin -----
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ----- Compose (BOM ujednolica wersje wszystkich bibliotek Compose poniżej) -----
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Rozszerzony zestaw ikon Material — kod używa m.in. VisibilityOff, Translate, Restore, Computer,
    // LocationOn, LockOpen, ContentCopy, ErrorOutline, WifiOff, Download, Lightbulb — części z nich
    // NIE MA w podstawowym pakiecie material-icons-core, stąd ta zależność jest konieczna.
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ----- Activity + Nawigacja + ViewModel w Compose -----
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    // ----- Room (baza danych: historia, zakładki) -----
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ----- DataStore (ustawienia: motyw, wyszukiwarka, adblock, gradient nowej karty itd.) -----
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
