import groovy.json.JsonSlurper
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.garemat.lunachron"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.garemat.lunachron"
        minSdk = 24
        targetSdk = 36
        versionCode = 20206
        versionName = "2.2.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasName = System.getenv("KEY_ALIAS")
            val keyPasswordValue = System.getenv("KEY_PASSWORD")

            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isCrunchPngs = false
            // Only apply signingConfig if it exists and storeFile is set.
            // fdroidserver strips the signingConfigs block before building, so
            // findByName (returns null) is used instead of getByName (throws).
            signingConfigs.findByName("release")?.takeIf { it.storeFile != null }?.let {
                signingConfig = it
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

// ---------------------------------------------------------------------------
// Task: download the pinned lunachron-data release assets into
// app/src/main/assets/ so the bundled seed is deterministic at build time.
// The pinned tag is read from data.version in the repo root — this file is
// committed and updated by CI on each app release, giving F-Droid and local
// builds a reproducible, auditable data snapshot.
// The assets/ folder is gitignored — these files are never committed.
// ---------------------------------------------------------------------------
val dataAssetsDir = file("src/main/assets")
val compendiumAsset = "compendium.json"
val dataRepo = "garemat/lunachron-data"
// Read pinned version from data.version in the repo root.
val dataVersionFile = rootProject.file("data.version")
val pinnedDataTag = if (dataVersionFile.exists()) dataVersionFile.readText().trim() else "v0.1.0"

tasks.register("downloadDataAssets") {
    description = "Downloads compendium.json from the pinned lunachron-data release (tag in data.version)."
    group = "lunachron"

    outputs.files(file("${dataAssetsDir}/$compendiumAsset"))

    onlyIf("compendium asset already present") {
        !file("${dataAssetsDir}/$compendiumAsset").exists()
    }

    doLast {
        dataAssetsDir.mkdirs()

        val tagUrl = "https://api.github.com/repos/$dataRepo/releases/tags/$pinnedDataTag"
        logger.lifecycle("Fetching data release $pinnedDataTag from $dataRepo...")
        @Suppress("UNCHECKED_CAST")
        val release = JsonSlurper().parseText(
            URI(tagUrl).toURL().openStream().bufferedReader().readText()
        ) as Map<String, Any>

        val tag = release["tag_name"] as String
        @Suppress("UNCHECKED_CAST")
        val assets = release["assets"] as List<Map<String, Any>>

        val asset = assets.firstOrNull { it["name"] == compendiumAsset }
            ?: error("Asset '$compendiumAsset' not found in release $tag")
        val downloadUrl = asset["browser_download_url"] as String
        logger.lifecycle("  Downloading $compendiumAsset from $tag...")
        URI(downloadUrl).toURL().openStream().use { input ->
            file("${dataAssetsDir}/$compendiumAsset").outputStream().use { output ->
                input.copyTo(output)
            }
        }

        logger.lifecycle("Data asset downloaded (release $tag).")
    }
}

tasks.named("preBuild") {
    dependsOn("downloadDataAssets")
}

// Force patched versions of vulnerable transitive dependencies.
configurations.all {
    resolutionStrategy.eachDependency {
        val group = requested.group
        val name  = requested.name
        when {
            group == "io.netty" -> useVersion("4.1.129.Final")
            group == "com.google.protobuf" && name == "protobuf-kotlin" -> useVersion("3.25.5")
            group == "com.google.protobuf" && name == "protobuf-java"   -> useVersion("3.25.5")
            group == "org.bitbucket.b_c"   && name == "jose4j"          -> useVersion("0.9.6")
            group == "org.jdom"            && name == "jdom2"            -> useVersion("2.0.6.1")
            group == "org.apache.commons"  && name == "commons-compress" -> useVersion("1.26.0")
            group == "org.apache.commons"  && name == "commons-lang3"    -> useVersion("3.18.0")
            // Compose BOM 2024.12.01 pins concurrent-futures strictly to 1.1.0, but
            // espresso-core:3.7.0 and junit:1.3.0 require 1.2.0 — override the strict constraint.
            group == "androidx.concurrent" && name == "concurrent-futures"     -> useVersion("1.2.0")
            group == "androidx.concurrent" && name == "concurrent-futures-ktx" -> useVersion("1.2.0")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.jsoup)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
