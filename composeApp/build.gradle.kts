import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.material.icons.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(project(":libreoffice-kit"))
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.pdfbox)
        }
    }
}

// Version comes from the GitHub release tag (e.g. v1.2.0) when built in the cloud.
val appVersion: String = System.getenv("APP_VERSION")
    ?.takeIf { it.matches(Regex("""\d+(\.\d+){0,2}""")) }
    ?.split('.')?.let { (it + listOf("0", "0")).take(3).joinToString(".") }
    ?: "1.0.0"
val appVersionCode: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val releaseKeystore: String? = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { file(it).exists() }

// The Android office engine is optional at build time. When the LibreOffice
// native library has been dropped into libreoffice-kit (see README), the app
// switches it on automatically and restricts itself to that CPU architecture.
val loEngineBundled = rootProject.file(
    "libreoffice-kit/src/main/jniLibs/arm64-v8a/liblo-native-code.so"
).exists()

android {
    namespace = "com.reader360.app"
    compileSdk = 35

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    defaultConfig {
        applicationId = "com.reader360.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("boolean", "LO_ENGINE_BUNDLED", loEngineBundled.toString())
        if (loEngineBundled) {
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        // LibreOffice loads its own libraries by name at runtime, so keep them
        // extracted on the device the classic way.
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with your own key when the GitHub secrets are set (so updates
            // install over the previous version); otherwise with the debug key.
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "com.reader360.app.MainKt"
        jvmArgs += listOf("-Xmx2g", "-Dsun.java2d.uiScale.enabled=true")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "360Reader"
            packageVersion = appVersion
            description = "Fast, faithful viewer for documents, slides, sheets, PDFs and images"
            vendor = "360Reader"
            copyright = "Copyright 2026 360Reader"
            modules("java.desktop", "java.logging", "java.naming", "jdk.unsupported")

            // Anything under resources/windows ends up next to the app. Drop a
            // portable LibreOffice into resources/windows/libreoffice to ship
            // the office engine inside the installer (see README).
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            windows {
                iconFile.set(project.file("icons/360reader.ico"))
                menuGroup = "360Reader"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f1b7d2e-3c4a-4e8b-9a51-2d7c0e9f4b13"
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}
