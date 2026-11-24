import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ioniconsVersion = "v8.1.0"
val ioniconNames = listOf(
    "home-outline", "book-outline", "search-outline", "reader-outline",
    "stats-chart-outline", "settings-outline", "chevron-back-outline",
    "chevron-forward-outline", "bookmark-outline", "checkmark-circle-outline",
    "time-outline", "school-outline", "language-outline", "library-outline",
    "swap-horizontal-outline", "close-outline", "information-circle-outline",
    "play-circle-outline", "document-text-outline", "filter-outline",
    "checkmark-outline", "help-circle-outline", "trophy-outline",
    "globe-outline", "arrow-forward-outline", "refresh-outline", "volume-high-outline",
    "list-outline", "bulb-outline", "heart-outline", "calendar-outline"
)

val prepareExternalAssets by tasks.registering {
    group = "build setup"
    description = "Downloads the Ionicons SVG assets and offline WikDict EN-PT / PT-EN databases."

    doLast {
        fun download(url: String, destination: File) {
            if (destination.exists() && destination.length() > 128) return
            destination.parentFile.mkdirs()
            val temporary = File(destination.parentFile, destination.name + ".part")
            try {
                URL(url).openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 90_000
                    setRequestProperty("User-Agent", "EnglishCourse-Android-Build/1.0")
                }.getInputStream().use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                check(temporary.length() > 128) { "Downloaded file is unexpectedly small: $url" }
                if (destination.exists()) destination.delete()
                check(temporary.renameTo(destination)) { "Could not move downloaded file to $destination" }
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }

        val assets = file("src/main/assets")
        val iconDirectory = File(assets, "ionicons")
        ioniconNames.forEach { name ->
            download(
                "https://raw.githubusercontent.com/ionic-team/ionicons/$ioniconsVersion/src/svg/$name.svg",
                File(iconDirectory, "$name.svg")
            )
        }

        val dictionaryDirectory = File(assets, "dictionary")
        val wikdictVersion = "2_2026-06"
        listOf("en-pt.sqlite3", "pt-en.sqlite3").forEach { name ->
            download(
                "https://download.wikdict.com/dictionaries/sqlite/$wikdictVersion/$name",
                File(dictionaryDirectory, name)
            )
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareExternalAssets)
}

android {
    namespace = "ao.creativespace.englishcourse"
    compileSdk = 35

    defaultConfig {
        applicationId = "ao.creativespace.englishcourse"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.caverock:androidsvg-aar:1.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
